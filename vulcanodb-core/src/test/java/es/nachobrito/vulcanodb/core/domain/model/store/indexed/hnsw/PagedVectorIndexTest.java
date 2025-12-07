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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author nacho
 */
class PagedVectorIndexTest {

    @Test
    void expectVectorAdded() {
        var index = new PagedVectorIndex(2, 2);
        assertEquals(0, index.addVector(new float[]{0, 1}));
        assertEquals(1, index.addVector(new float[]{2, 3}));
        assertEquals(2, index.addVector(new float[]{4, 5}));

        assertEquals(2, index.getPageCount());

        assertEquals(0, index.get(0, 0));
        assertEquals(1, index.get(0, 1));
        assertEquals(2, index.get(1, 0));
        assertEquals(3, index.get(1, 1));
        assertEquals(4, index.get(2, 0));
        assertEquals(5, index.get(2, 1));
    }

}