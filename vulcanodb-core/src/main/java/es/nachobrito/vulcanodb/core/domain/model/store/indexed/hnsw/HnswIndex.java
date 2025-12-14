/*
 *    Copyright 2025 Nacho Brito
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package es.nachobrito.vulcanodb.core.domain.model.store.indexed.hnsw;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 *
 * @author nacho
 */
public class HnswIndex {
    private final HnswConfig config;
    private final PagedVectorIndex layer0;
    private final PagedGraphIndex layer0Connections;

    /*
    Only ~5% of the nodes exist in Layer 1, and ~0.25% in Layer 2. The memory overhead of Java objects here is
    negligible compared to the massive Layer 0.
     */
    private final Map<Integer, Map<Long, Set<Long>>> upperLayerConnections = new HashMap<>();

    private int globalMaxLayer = 0;
    private long globalEnterPoint = 0;

    public HnswIndex(HnswConfig config) {
        this.config = config;
        layer0 = new PagedVectorIndex(config.blockSize(), config.dimensions());
        layer0Connections = new PagedGraphIndex(config.mMax0(), config.blockSize());
    }

    /**
     *
     * @return the index configuration
     */
    HnswConfig getConfig() {
        return config;
    }

    /**
     * Inserts a new vector to the index
     *
     * @param newVector the new vector
     * @return the id of the new vector within the index
     */
    public long insert(float[] newVector) {
        if (newVector.length != config.dimensions()) {
            throw new IllegalArgumentException(
                    "Invalid vector dimension: %d (expected %d)".formatted(newVector.length, config.dimensions()));
        }

        long currentId = globalEnterPoint;
        int maxL = globalMaxLayer;

        //1. Randomly choose a maximum layer
        int vectorMaxLayer = determineMaxLayer();

        //2. Add vector to layer 0
        long newId = layer0.addVector(newVector);

        //3. Zoom down from Top Layer to vectorMaxLayer+1 (Greedy search, ef=1)
        for (int l = globalMaxLayer; l > vectorMaxLayer; l--) {
            currentId = searchLayerGreedy(newVector, currentId, l);
        }

        // 4. From L down to 0, find neighbors and link
        for (int layer = Math.min(vectorMaxLayer, maxL); layer >= 0; layer--) {
            // Search layer with efConstruction
            var neighborCandidates = searchLayer(newVector, currentId, layer, config.efConstruction());
            if (neighborCandidates.isEmpty()) {
                continue;
            }

            // Update entry point for next layer
            currentId = neighborCandidates.peek().vectorId(); // Rough logic, usually closest

            // Select neighbors using heuristic
            var neighbors = selectNeighborsHeuristic(neighborCandidates);

            // Add connections bidirectional
            for (long neighborId : neighbors) {
                // Register connection bidirectionally. Not both connections are guaranteed to be stored,
                // as there will be a pruning process for each node.
                addConnection(newId, neighborId, layer);
                addConnection(neighborId, newId, layer);
            }
        }

        // 5. Update global entry point if new node is in a higher layer
        if (vectorMaxLayer > globalMaxLayer) {
            globalMaxLayer = vectorMaxLayer;
            globalEnterPoint = newId;
        }
        return newId;
    }

    /**
     * Adds vector 2 as connection to vector 1 in layer.
     * <p>
     * If neighborId's connection list exceeds M_max, shrink it using the heuristic
     *
     * @param vectorId1 the vector 1 id
     * @param vectorId2 the vector 2 id
     * @param layer     the layer index
     */
    private void addConnection(long vectorId1, long vectorId2, int layer) {
        var connections = getConnections(vectorId1, layer);
        var currentCount = connections.size();
        
        connections.add(vectorId2);

        if (currentCount >= config.mMax()) {
            connections = shrinkConnections(vectorId1, connections);
        }

        if (layer == 0) {
            layer0Connections.setConnections(vectorId1, connections);
            return;
        }

        var layerConnections = upperLayerConnections.computeIfAbsent(layer, k -> new HashMap<>());
        layerConnections.put(vectorId1, connections);
    }

    /**
     * Uses the neighbor selection heuristic to shrink the number of connections of a vector
     *
     * @param vectorId           the vector id
     * @param currentConnections the current connections
     * @return the new list of connections
     */
    private Set<Long> shrinkConnections(long vectorId, Set<Long> currentConnections) {
        var vector = layer0.getVector(vectorId);
        var similarity = config.vectorSimilarity();
        var candidates = new PriorityQueue<>(Comparator.comparingDouble(NodeSimilarity::similarity).reversed());

        currentConnections
                .stream()
                .map(id -> new NodeSimilarity(id, similarity.between(vector, layer0.getVector(id))))
                .forEach(candidates::add);

        return selectNeighborsHeuristic(candidates);

    }

    /**
     * Connectivity vs. Density:
     * <p>
     * When inserting a new node q, a simple approach would be to connect it to the M closest neighbors found during
     * the search.
     * </p><p>
     * The Flaw: If the data is highly clustered, the M closest neighbors might all belong to the same dense, local
     * cluster.
     * This results in a node q having excellent local connections but poor long-range connections, making the greedy
     * search get stuck in the local cluster and fail to find true nearest neighbors that are just outside.
     * </p><p>
     * Algorithm 4 solves this by introducing a diversity criterion to prune the candidate list. For each candidate Ci
     * we calculate the distance from Ci to the other already selected neighbors.
     * </p><p>
     * Decision Logic:
     * <ul>
     * <li>If Ci is closer to q than it is to any existing selected neighbor r, this means Ci provides new, distinct
     * information about the area surrounding q, so we select Ci and add it to the result list.</li>
     *
     * <li>If Ci is closer to an existing selected neighbor r than it is to q, this means Ci is essentially in
     * the "shadow" of an already selected neighbor r, and connecting to it would be redundant, so we discard
     * Ci (prune it).</li>
     * </ul>
     * </p>
     *
     * @param candidates the efConstruction closest nodes
     * @return the list of selected neighbors
     */
    private Set<Long> selectNeighborsHeuristic(PriorityQueue<NodeSimilarity> candidates) {
        Set<Long> neighbors = new HashSet<>();

        // candidates is a min-queue (closest first)
        while (!candidates.isEmpty() && neighbors.size() < config.m()) {
            var current = candidates.poll();
            var candidateId = current.vectorId();
            var candidate = layer0.getVector(candidateId);
            // Distance between candidate and Query
            var candidateToQuery = current.similarity();

            var isCloserToQ = true;
            var similarity = config.vectorSimilarity();
            for (long neighborId : neighbors) {
                var neighbor = layer0.getVector(neighborId);
                // Distance between candidate and already selected neighbor
                var candidateToNeighbor = similarity.between(candidate, neighbor);
                // If current is closer to an existing neighbor r than to q, skip it (diversity check)
                if (candidateToNeighbor > candidateToQuery) {
                    isCloserToQ = false;
                    break;
                }
            }

            if (isCloserToQ) {
                neighbors.add(candidateId);
            }
        }
        return neighbors;
    }

    private int determineMaxLayer() {
        // - ThreadLocalRandom.current().nextDouble() is a double between 0.0 and 1.0
        // - Since the logarithm of a number between 0 and 1 is negative, we change the sign
        // - mL is an input parameter to the algorithm
        //
        // As a result, l is a random number between 0 and mL, but values close to 0
        // are much more probable.
        var random = ThreadLocalRandom.current().nextDouble();
        var log = -Math.log(random);
        var value = log * config.mL();
        var maxLayer = (int) Math.round(value);
        return maxLayer;
    }


    /**
     * Find vectors similar to the one provided within a layer
     *
     * @param vector       the vector to compare with
     * @param enterPointId the initial entry point
     * @param layer        the layer index
     * @param ef           Number of candidates to explore ("exploration factor")
     * @return a PriorityQueue with the top closest vectors
     */
    private PriorityQueue<NodeSimilarity> searchLayer(float[] vector, long enterPointId, int layer, int ef) {
        var similarity = config.vectorSimilarity();

        // Visited set to avoid loops
        Set<Long> visited = new HashSet<>();
        visited.add(enterPointId);

        // 1. Candidates (Min-Heap): Order by best similarity (highest score first)
        // Uses NodeSimilarity's natural ordering (which orders by sim DESC)
        PriorityQueue<NodeSimilarity> candidates = new PriorityQueue<>();

        // 2. Nearest Neighbors (Max-Heap): Order by worst similarity (lowest score at the top)
        // Used to track the FURTHEST element in the result set W, which is the SMALLEST similarity.
        PriorityQueue<NodeSimilarity> nearestNeighbors = new PriorityQueue<>(Comparator.comparingDouble(NodeSimilarity::similarity));

        var entryPoint = layer0.getVector(enterPointId);
        float sim = similarity.between(vector, entryPoint);
        NodeSimilarity epSim = new NodeSimilarity(enterPointId, sim);
        candidates.add(epSim);
        nearestNeighbors.add(epSim);
        while (!candidates.isEmpty()) {
            // Best candidate (highest similarity)
            NodeSimilarity current = candidates.poll();
            // Furthest neighbor found so far (lowest similarity)
            NodeSimilarity furthestResult = nearestNeighbors.peek();

            // Stopping Condition: Check if the best candidate is WORSE than the worst result
            // If candidate's similarity is less than the worst result, STOP.
            if (current.similarity() < furthestResult.similarity()) {
                break;
            }

            var currentId = current.vectorId();
            var connections = getConnections(currentId, layer);
            for (long neighborId : connections) {
                if (visited.contains(neighborId)) {
                    continue;
                }
                visited.add(neighborId);
                var neighbor = layer0.getVector(neighborId);
                float neighborSim = similarity.between(vector, neighbor);
                var currentWorstSim = nearestNeighbors.peek().similarity();
                if (neighborSim > currentWorstSim || nearestNeighbors.size() < ef) {
                    NodeSimilarity nodeSimilarity = new NodeSimilarity(neighborId, neighborSim);
                    candidates.add(nodeSimilarity);
                    nearestNeighbors.add(nodeSimilarity);

                    if (nearestNeighbors.size() > ef) {
                        // Remove the element with the lowest similarity (worst result)
                        nearestNeighbors.poll();
                    }
                }

            }
        }
        return nearestNeighbors;
    }

    /**
     * Simple greedy search for a single closest neighbor (ef=1)
     *
     * @param query      the query vector
     * @param entryPoint the id of the entry point vector
     * @param layer      the layer
     * @return the id of the closest node to q in the given layer
     */
    private long searchLayerGreedy(float[] query, long entryPoint, int layer) {
        var similarity = config.vectorSimilarity();
        long bestNode = entryPoint;
        float maxContextSim = similarity.between(query, layer0.getVector(bestNode));
        boolean changed = true;

        while (changed) {
            changed = false;
            var neighbors = getConnections(bestNode, layer);
            for (long neighborId : neighbors) {
                float sim = similarity.between(query, layer0.getVector(neighborId));
                if (sim > maxContextSim) {
                    maxContextSim = sim;
                    bestNode = neighborId;
                    changed = true;
                }
            }
        }
        return bestNode;
    }

    private Set<Long> getConnections(long currentId, int layer) {
        if (layer == 0) {
            var connections = layer0Connections.getConnections(currentId);
            return Arrays.stream(connections)
                    .boxed()
                    .collect(Collectors.toCollection(HashSet::new));
        }

        var layerConnections = upperLayerConnections.get(layer);
        if (layerConnections == null) {
            return new HashSet<>();
        }

        var connections = layerConnections.get(currentId);
        if (connections == null) {
            return new HashSet<>();
        }
        return connections;
    }


    /**
     * Returns the K-Nearest Neighbors of queryVector
     *
     * @param queryVector the query vector
     * @param k           the number of vectors to return
     * @return a list of {@link NodeSimilarity} instances representing the search results.
     */
    public List<NodeSimilarity> search(float[] queryVector, int k) {
        long currObj = globalEnterPoint;

        // 1. Zoom-in Phase: Fast greedy search through upper layers
        // We use ef=1 for speed as we just need a good entry point for the next layer
        for (int l = globalMaxLayer; l >= 1; l--) {
            currObj = searchLayerGreedy(queryVector, currObj, l);
        }

        // 2. Fine Search Phase: Comprehensive search at the ground layer (Layer 0)
        // W is a Max-Heap that keeps track of the 'ef' best candidates
        PriorityQueue<NodeSimilarity> w = searchLayer(queryVector, currObj, 0, config.efSearch());

        // 3. Return top K results from the candidates found in Layer 0
        List<NodeSimilarity> results = new ArrayList<>();
        // W is a max-heap; we want the closest K, so we might need to sort or poll
        while (w.size() > k) {
            w.poll(); // Remove furthest until we have K
        }
        while (!w.isEmpty()) {
            results.add(w.poll());
        }
        Collections.reverse(results);
        return results;
    }

    /**
     * Returns the vector stored with the given id, if present.
     *
     * @param id the id to search
     * @return the vector associated to that id, or .
     */
    public Optional<float[]> get(long id) {
        if (id < 0 || id > this.layer0.getVectorCount()) {
            return Optional.empty();
        }
        return Optional.of(layer0.getVector(id));
    }
}
