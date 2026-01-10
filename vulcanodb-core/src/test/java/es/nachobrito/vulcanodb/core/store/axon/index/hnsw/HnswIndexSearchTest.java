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
public class HnswIndexSearchTest {

    private Path path;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test-hnsw-search");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectVectorSearch() throws Exception {
        var config = HnswConfig.builder()
                .withDimensions(2)
                .withEfSearch(500) //max recall
                .withEfConstruction(500) // max recall
                .withML(0)//single layer
                .build();
        try (var index = new HnswIndex(config, path)) {
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
}
