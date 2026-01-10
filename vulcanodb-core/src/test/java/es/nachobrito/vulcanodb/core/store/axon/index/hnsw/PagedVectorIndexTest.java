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

import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class PagedVectorIndexTest {

    private Path path;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test-vector");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectVectorAdded() {
        try (var index = new PagedVectorIndex(2, 2, path)) {
            assertEquals(0, index.addVector(new float[]{0, 1}));
            assertEquals(1, index.addVector(new float[]{2, 3}));
            assertEquals(2, index.addVector(new float[]{4, 5}));

            assertEquals(2, index.getPageCount());

            assertEquals(0, index.getElement(0, 0));
            assertEquals(1, index.getElement(0, 1));
            assertEquals(2, index.getElement(1, 0));
            assertEquals(3, index.getElement(1, 1));
            assertEquals(4, index.getElement(2, 0));
            assertEquals(5, index.getElement(2, 1));

            assertArrayEquals(new float[]{0, 1}, index.getVector(0));
            assertArrayEquals(new float[]{2, 3}, index.getVector(1));
            assertArrayEquals(new float[]{4, 5}, index.getVector(2));
        }
    }

    @Test
    void expectErrorIfConstructorArgsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PagedVectorIndex(0, 0, path);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new PagedVectorIndex(1, 0, path);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new PagedVectorIndex(0, 1, path);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new PagedVectorIndex(1, -1, path);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new PagedVectorIndex(-1, 1, path);
        });

    }

    @Test
    void expectErrorIfInvalidId() {
        try (var index = new PagedVectorIndex(2, 2, path)) {
            index.addVector(new float[]{0, 1});
            index.addVector(new float[]{2, 3});
            index.addVector(new float[]{4, 5});

            assertThrows(IllegalArgumentException.class, () -> {
                index.getVector(3);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                index.getElement(-1, 0);
            });
            assertThrows(IllegalArgumentException.class, () -> {
                index.getElement(0, -1);
            });
            assertThrows(IllegalArgumentException.class, () -> {
                index.getElement(0, 2);
            });
            assertThrows(IllegalArgumentException.class, () -> {
                index.getElement(3, 0);
            });
        }
    }

}
