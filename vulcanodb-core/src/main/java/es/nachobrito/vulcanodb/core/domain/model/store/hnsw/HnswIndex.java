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

package es.nachobrito.vulcanodb.core.domain.model.store.hnsw;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nacho
 */
public class HnswIndex {
    private final HnswConfig config;
    private final List<PagedVectorIndex> layers = new ArrayList<>();

    public HnswIndex(HnswConfig config) {
        this.config = config;
        buildLayers();
    }

    private void buildLayers() {
        //todo
        //layer 0
        this.layers.add(new PagedVectorIndex(config.blockSize(), config.dimensions()));
    }

    /**
     * Inserts a new vector to the index
     *
     * @param newVector the new vector
     * @return the id of the new vector within the index
     */
    public long insert(float[] newVector) {
        var newIndex = layers.getFirst().addVector(newVector);

        return newIndex;
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
}
