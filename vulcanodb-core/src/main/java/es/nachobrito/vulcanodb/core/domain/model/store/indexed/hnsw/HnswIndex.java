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
    private final Map<Integer, Map<Long, long[]>> upperLayerConnections = new HashMap<>();

    private int globalMaxLayer = 0;
    private long globalEnterPoint = 0;

    private record NodeSimilarity(long vectorId, float similarity) {
    }

    public HnswIndex(HnswConfig config) {
        this.config = config;
        layer0 = new PagedVectorIndex(config.blockSize(), config.dimensions());
        layer0Connections = new PagedGraphIndex(config.mMax0(), config.blockSize());
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
        int l = determineMaxLayer();

        //2. Add vector to layer 0
        long newId = layer0.addVector(newVector);

        //3. Zoom down from Top Layer to l+1 (Greedy search, ef=1)
        currentId = this.determineEnterPoint(newVector, currentId, maxL, l);

        // 4. From L down to 0, find neighbors and link
        for (int i = Math.min(l, maxL); i >= 0; i--) {
            // Search layer with efConstruction
            var neighborCandidates = searchLayer(newVector, currentId, i, config.efConstruction());
            if (neighborCandidates.isEmpty()) {
                continue;
            }

            // Update entry point for next layer
            currentId = neighborCandidates.peek().vectorId(); // Rough logic, usually closest

            // Select neighbors using heuristic
            var neighbors = selectNeighborsHeuristic(neighborCandidates);

            // Add connections bidirectional
            for (long neighborId : neighbors) {
                // Add q to neighborId's connections
                addConnection(newId, neighborId, i);
            }
        }

        // 5. Update global entry point if new node is in a higher layer
        if (l > globalMaxLayer) {
            globalMaxLayer = l;
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
        var currentConnections = getConnections(vectorId1, layer);
        var currentCount = currentConnections.length;

        var newConnections = new long[1 + currentConnections.length];
        newConnections[currentCount] = vectorId2;

        if (currentCount >= config.mMax()) {
            newConnections = shrinkConnections(vectorId1, newConnections);
        }

        if (layer == 0) {
            layer0Connections.setConnections(vectorId1, newConnections);
            return;
        }

        var layerConnections = upperLayerConnections.computeIfAbsent(layer, k -> new HashMap<>());
        layerConnections.put(vectorId1, newConnections);
    }

    /**
     * Uses the neighbor selection heuristic to shrink the number of connections of a vector
     *
     * @param vectorId           the vector id
     * @param currentConnections the current connections
     * @return the shrunk connections array
     */
    private long[] shrinkConnections(long vectorId, long[] currentConnections) {
        var vector = layer0.getVector(vectorId);
        var similarity = config.vectorSimilarity();
        var candidates = new PriorityQueue<>(Comparator.comparingDouble(NodeSimilarity::similarity).reversed());

        Arrays.stream(currentConnections)
                .boxed()
                .map(id -> new NodeSimilarity(id, similarity.between(vector, layer0.getVector(id))))
                .forEach(candidates::add);

        var boxed = selectNeighborsHeuristic(candidates).toArray(new Long[0]);

        return Arrays.stream(boxed).mapToLong(Long::longValue).toArray();
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
    private List<Long> selectNeighborsHeuristic(PriorityQueue<NodeSimilarity> candidates) {
        List<Long> neighbors = new ArrayList<>();

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
        return (int) (-Math.log(ThreadLocalRandom.current().nextDouble()) * config.mL());
    }

    /**
     * Find the closest vector in layers > l
     *
     * @param newVector the vector to compare with
     * @param currentId the current entry point
     * @param maxL      the maximum layer
     * @param l         the boundary layer
     * @return the id of the closest vector in a layer > l
     */
    private long determineEnterPoint(float[] newVector, long currentId, int maxL, int l) {
        var result = currentId;
        var minDistance = Float.MAX_VALUE;
        for (int i = maxL; i > l; i--) {
            var nodeDist = searchLayer(newVector, currentId, i, 1).peek();
            if (nodeDist.similarity() < minDistance) {
                minDistance = nodeDist.similarity();
                result = nodeDist.vectorId();
            }
        }
        return result;
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
        // Visited set to avoid loops
        Set<Long> visited = new HashSet<>();
        visited.add(enterPointId);

        // Candidates (Min-Heap): Best nodes to explore next
        PriorityQueue<NodeSimilarity> candidates = new PriorityQueue<>(Comparator.comparingDouble(NodeSimilarity::similarity).reversed());

        // Found results (Max-Heap): Keeps track of the 'ef' closest nodes found so far
        // We need Max-Heap to easily remove the furthest element if the list gets too big
        PriorityQueue<NodeSimilarity> nearestNeighbors = new PriorityQueue<>((a, b) -> -Float.compare(b.similarity(), a.similarity()));

        float dist = config.vectorSimilarity().between(vector, layer0.getVector(enterPointId));
        NodeSimilarity epDist = new NodeSimilarity(enterPointId, dist);
        candidates.add(epDist);
        nearestNeighbors.add(epDist);
        var similarity = config.vectorSimilarity();
        while (!candidates.isEmpty()) {
            NodeSimilarity current = candidates.poll();
            NodeSimilarity furthestResult = nearestNeighbors.peek(); // The worst of the best so far

            // Optimization: if the best candidate is worse than the worst neighbor we've already found, stop.
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
                float s = similarity.between(vector, neighbor);

                if (s > nearestNeighbors.peek().similarity() || nearestNeighbors.size() < ef) {
                    NodeSimilarity nd = new NodeSimilarity(neighborId, s);
                    candidates.add(nd);
                    nearestNeighbors.add(nd);

                    if (nearestNeighbors.size() > ef) {
                        nearestNeighbors.poll(); // Remove the furthest
                    }
                }

            }
        }
        return nearestNeighbors; // This actually returns a Max-Heap, generally you convert to list
    }

    private long[] getConnections(long currentId, int layer) {
        if (layer == 0) {
            return layer0Connections.getConnections(currentId);
        }
        var connections = upperLayerConnections.get(layer).get(currentId);
        if (connections == null) {
            return new long[0];
        }
        return connections;
    }


    /**
     * Returns the K-Nearest Neighbors of queryVector
     *
     * @param queryVector the query vector
     * @return a list of {@link Match} instances representing the search results.
     */
    public List<Match> search(float[] queryVector) {
        return List.of();
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
