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

import es.nachobrito.vulcanodb.core.query.similarity.CosineSimilarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author nacho
 */
class HnswConfigTest {

    @Test
    void expectDefaults() {
        var config = HnswConfig.builder().build();
        assertInstanceOf(CosineSimilarity.class, config.vectorSimilarity());
        assertEquals(1_048_576, config.blockSize());
        assertEquals(384, config.dimensions());
        assertEquals(100, config.efConstruction());
        assertEquals(50, config.efSearch());
        assertEquals(16, config.m());
        assertEquals(16, config.mMax());
        assertEquals(32, config.mMax0());
        assertEquals((float) (1.0 / Math.log(16)), config.mL());
    }
}