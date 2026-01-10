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

package es.nachobrito.vulcanodb.core.store.axon.index.hnsw;

import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author nacho
 */
public final class HnswIndex implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HnswConfig config;
    private final PagedVectorIndex layer0;
    private final Map<Integer, PagedGraphIndex> graphs = new ConcurrentHashMap<>();
    private final Path basePath;
    private final KeyValueStore metadataStore;

    private int globalMaxLayer = 0;
    private long globalEnterPoint = 0;

    public HnswIndex(HnswConfig config, Path basePath) {
        this.config = config;
        this.basePath = basePath;

        if (basePath == null) {
            throw new IllegalArgumentException("basePath cannot be null");
        }

        try {
            Files.createDirectories(basePath);
            this.metadataStore = new KeyValueStore(basePath.resolve("metadata"));
            loadMetadata();
            this.layer0 = new PagedVectorIndex(config.blockSize(), config.dimensions(), basePath.resolve("vectors"));
            loadExistingGraphs();
        } catch (IOException e) {
            throw new AxonDataStoreException(e);
        }
    }

    private void loadMetadata() {
        metadataStore.getInt("maxLayer").ifPresent(val -> globalMaxLayer = val);
        metadataStore.getInt("enterPoint").ifPresent(val -> globalEnterPoint = (long) val);
    }

    private void loadExistingGraphs() throws IOException {
        // Layer 0 graph is mandatory and usually exists or is created
        graphs.put(0, new PagedGraphIndex(config.mMax0(), config.blockSize(), basePath.resolve("graph_layer_0")));

        // Load upper layer graphs based on globalMaxLayer
        for (int i = 1; i <= globalMaxLayer; i++) {
            Path graphPath = basePath.resolve("graph_layer_" + i);
            if (Files.exists(graphPath)) {
                graphs.put(i, new PagedGraphIndex(config.mMax(), config.blockSize(), graphPath));
            }
        }
    }

    private void persistMetadata() {
        metadataStore.putInt("maxLayer", globalMaxLayer);
        metadataStore.putInt("enterPoint", (int) globalEnterPoint);
    }

    private PagedGraphIndex getGraph(int layer) {
        return graphs.computeIfAbsent(layer, l -> {
            int mMax = (l == 0) ? config.mMax0() : config.mMax();
            return new PagedGraphIndex(mMax, config.blockSize(), basePath.resolve("graph_layer_" + l));
        });
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
        if (log.isDebugEnabled()) {
            log.debug("Vector {} will have max layer {}", newId, vectorMaxLayer);
        }
        //3. Zoom down from Top Layer to vectorMaxLayer+1 (Greedy search, ef=1)
        for (int l = globalMaxLayer; l > vectorMaxLayer; l--) {
            currentId = searchLayerGreedy(newVector, currentId, l);
        }

        // 4. From L down to 0, find neighbors and link
        for (int layer = Math.min(vectorMaxLayer, maxL); layer >= 0; layer--) {
            // Search layer with efConstruction
            var neighborCandidates = searchLayer(newVector, currentId, layer, config.efConstruction());
            if (log.isDebugEnabled()) {
                log.debug("Vector {} has {} neighbor candidates in layer {}", newId, neighborCandidates.size(), layer);
            }
            if (neighborCandidates.isEmpty()) {
                continue;
            }

            // Update entry point for next layer
            currentId = neighborCandidates.peek().vectorId(); // Rough logic, usually closest

            // Select neighbors using heuristic
            var neighbors = selectNeighborsHeuristic(neighborCandidates);
            if (log.isDebugEnabled()) {
                log.debug("Vector {} has {} neighbors in layer {}", newId, neighbors.size(), layer);
            }
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
            persistMetadata();
        }
        return newId;
    }

    /**
     * Adds vector 2 as connection to vector 1 in layer.
     */
    private void addConnection(long vectorId1, long vectorId2, int layer) {
        if (log.isDebugEnabled()) {
            log.debug("Adding vector {} as connection of vector {} in layer {}", vectorId2, vectorId1, layer);
        }
        var connections = getConnections(vectorId1, layer);
        var currentCount = connections.length;

        // Note: we can't easily add to primitive array, but PagedGraphIndex.addConnection handles it
        var graph = getGraph(layer);

        // We need to check if we need to shrink before adding or after. 
        // Existing logic used setConnections with a Set for shrinking.
        if (currentCount >= (layer == 0 ? config.mMax0() : config.mMax())) {
            Set<Long> connectionSet = new HashSet<>();
            for (long c : connections) connectionSet.add(c);
            connectionSet.add(vectorId2);
            var shrunk = shrinkConnections(vectorId1, connectionSet);
            graph.setConnections(vectorId1, shrunk);
        } else {
            graph.addConnection(vectorId1, vectorId2);
        }
    }

    /**
     * Uses the neighbor selection heuristic to shrink the number of connections of a vector
     */
    private Set<Long> shrinkConnections(long vectorId, Set<Long> currentConnections) {
        var vector = layer0.getVector(vectorId);
        var similarity = config.vectorSimilarity();
        var candidates = new PriorityQueue<>(Comparator.comparingDouble(NodeSimilarity::similarity).reversed());

        for (long id : currentConnections) {
            candidates.add(new NodeSimilarity(id, similarity.between(vector, layer0.getVector(id))));
        }

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
     */
    private Set<Long> selectNeighborsHeuristic(PriorityQueue<NodeSimilarity> candidates) {
        Set<Long> neighbors = new HashSet<>();
        while (!candidates.isEmpty() && neighbors.size() < config.m()) {
            var current = candidates.poll();
            var candidateId = current.vectorId();
            var candidate = layer0.getVector(candidateId);
            var candidateToQuery = current.similarity();

            var isCloserToQ = true;
            var similarity = config.vectorSimilarity();
            for (long neighborId : neighbors) {
                var neighbor = layer0.getVector(neighborId);
                var candidateToNeighbor = similarity.between(candidate, neighbor);
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
        var logVal = -Math.log(random);
        var value = logVal * config.mL();
        return (int) Math.round(value);
    }


    /**
     * Find vectors similar to the one provided within a layer
     */
    private PriorityQueue<NodeSimilarity> searchLayer(float[] vector, long enterPointId, int layer, int ef) {
        var similarity = config.vectorSimilarity();
        Roaring64Bitmap visited = new Roaring64Bitmap();
        visited.add(enterPointId);

        PriorityQueue<NodeSimilarity> candidates = new PriorityQueue<>();
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

    private long[] getConnections(long currentId, int layer) {
        var graph = getGraph(layer);
        return graph.getConnections(currentId);
    }


    /**
     * Returns the K-Nearest Neighbors of queryVector
     */
    public List<NodeSimilarity> search(float[] queryVector, int k) {
        if (layer0.getVectorCount() == 0) {
            return Collections.emptyList();
        }

        long currObj = globalEnterPoint;
        if (log.isDebugEnabled()) {
            log.debug("Starting search at vector {}", currObj);
        }
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
        while (w.size() > k) {
            w.poll();
        }
        while (!w.isEmpty()) {
            results.add(w.poll());
        }
        Collections.reverse(results);
        return results;
    }

    /**
     * Returns the vector stored with the given id, if present.
     */
    public Optional<float[]> get(long id) {
        if (id < 0 || id >= this.layer0.getVectorCount()) {
            return Optional.empty();
        }
        return Optional.of(layer0.getVector(id));
    }

    @Override
    public void close() throws Exception {
        persistMetadata();
        metadataStore.close();
        layer0.close();
        for (var g : graphs.values()) g.close();
    }
}
