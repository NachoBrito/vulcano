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

package es.nachobrito.vulcanodb.core.domain.model.store.axon.index.hnsw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
public class HnswIndexSearchTest {

    @Test
    void expectVectorSearch() {
        var config = HnswConfig.builder()
                .withDimensions(2)
                .withEfSearch(500) //max recall
                .withEfConstruction(500) // max recall
                .withML(0)//single layer
                .build();
        var index = new HnswIndex(config);

        index.insert(new float[]{0.5f, 1});
        index.insert(new float[]{1, 0.5f});
        index.insert(new float[]{1, 1});
        index.insert(new float[]{.5f, .5f});

        var result = index.search(new float[]{.5f, .5f}, 5);
        assertNotNull(result);
        assertTrue(result.size() <= 5);
        assertFalse(result.isEmpty());
        assertTrue(result.contains(new NodeSimilarity(2, 1.0f)));
        assertTrue(result.contains(new NodeSimilarity(3, 1.0f)));
    }
}
