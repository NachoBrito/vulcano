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
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class HnswIndexCreateTest {

    private Path path;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test-hnsw-create");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectBoundaryChecks() throws Exception {
        var config = HnswConfig.builder().withDimensions(2).build();
        try (var index = new HnswIndex(config, path)) {
            assertThrows(IllegalArgumentException.class, () -> {
                index.insert(new float[]{1});
            });
            assertThrows(IllegalArgumentException.class, () -> {
                index.insert(new float[]{1, 2, 3});
            });
        }
    }

    @Test
    void expectIndexCreated() throws Exception {
        var config = HnswConfig.builder().build();
        try (var index = new HnswIndex(config, path)) {
            assertNotNull(index);

            var vectorCount = 1000;
            var vectors = createRandomVectors(vectorCount, config);
            var vectorIds = Arrays
                    .stream(vectors)
                    .map(index::insert)
                    .toArray(Long[]::new);

            var storedVectors = Arrays
                    .stream(vectorIds)
                    .sorted()
                    .map(index::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toArray(float[][]::new);

            assertArrayEquals(vectors, storedVectors);
        }
    }

    private float[][] createRandomVectors(int count, HnswConfig config) {
        var vectors = new float[count][config.dimensions()];
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            vectors[i] = new float[config.dimensions()];
            for (int j = 0; j < config.dimensions(); j++) {
                vectors[i][j] = random.nextFloat();
            }
        }
        return vectors;
    }


}
