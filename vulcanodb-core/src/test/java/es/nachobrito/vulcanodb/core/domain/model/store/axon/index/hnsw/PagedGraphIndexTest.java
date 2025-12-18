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
class PagedGraphIndexTest {

    @Test
    void expectConnectionsSet() {
        var index = new PagedGraphIndex(2, 2);
        index.setConnections(1, new long[]{2, 3});

        var buffer = new long[2];
        var count = index.getConnections(1, buffer);

        assertEquals(2, count);
        assertArrayEquals(new long[]{2, 3}, buffer);

        var connections = index.getConnections(1);
        assertArrayEquals(new long[]{2, 3}, connections);
    }

    @Test
    void expectConnectionsAdded() {
        var index = new PagedGraphIndex(2, 2);
        index.addConnection(1, 2);
        index.addConnection(1, 3);

        var buffer = new long[2];
        var count = index.getConnections(1, buffer);

        assertEquals(2, count);
        assertArrayEquals(new long[]{2, 3}, buffer);
    }

    @Test
    void expectBoundaryLimit() {
        var index = new PagedGraphIndex(1, 2);
        index.addConnection(1, 2);

        assertThrows(IllegalArgumentException.class, () -> {
            index.setConnections(1, new long[]{1, 2, 3});
        });
        assertThrows(IllegalArgumentException.class, () -> {
            index.addConnection(1, 3);
        });

    }
}