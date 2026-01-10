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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
public class HnswIndexPersistenceTest {

    private Path path;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test-hnsw-persistence");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectIndexPersisted() throws Exception {
        var config = HnswConfig.builder()
                .withDimensions(2)
                .withEfSearch(100)
                .withEfConstruction(100)
                .build();

        float[][] vectors = {
                {0.1f, 0.1f},
                {0.2f, 0.2f},
                {0.5f, 0.5f},
                {0.9f, 0.9f}
        };

        List<NodeSimilarity> originalResults;

        // 1. Create and populate index
        try (var index = new HnswIndex(config, path)) {
            for (float[] v : vectors) {
                index.insert(v);
            }
            originalResults = index.search(new float[]{0.4f, 0.4f}, 2);
            assertFalse(originalResults.isEmpty());
        }

        // 2. Reopen and verify
        try (var index = new HnswIndex(config, path)) {
            // Verify search results are identical
            var newResults = index.search(new float[]{0.4f, 0.4f}, 2);
            assertEquals(originalResults.size(), newResults.size());
            for (int i = 0; i < originalResults.size(); i++) {
                assertEquals(originalResults.get(i).vectorId(), newResults.get(i).vectorId());
                assertEquals(originalResults.get(i).similarity(), newResults.get(i).similarity());
            }

            // Verify vector retrieval
            for (int i = 0; i < vectors.length; i++) {
                var v = index.get(i);
                assertTrue(v.isPresent());
                assertArrayEquals(vectors[i], v.get());
            }
        }
    }

    @Test
    void expectMultiLayerPersistence() throws Exception {
        // Use very low mL to force multiple layers with few vectors
        var config = HnswConfig.builder()
                .withDimensions(2)
                .withML(1) // high probability of upper layers
                .build();

        // Populate enough vectors to ensure we have upper layers
        try (var index = new HnswIndex(config, path)) {
            for (int i = 0; i < 100; i++) {
                index.insert(new float[]{i / 100.0f, i / 100.0f});
            }
        }

        // Reopen and verify we can still search
        try (var index = new HnswIndex(config, path)) {
            var results = index.search(new float[]{0.5f, 0.5f}, 5);
            assertFalse(results.isEmpty());
            assertEquals(5, results.size());
        }
    }
}
