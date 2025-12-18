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

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class HnswIndexCreateTest {


    @Test
    void expectBoundaryChecks() {
        var config = HnswConfig.builder().withDimensions(2).build();
        var index = new HnswIndex(config);
        assertThrows(IllegalArgumentException.class, () -> {
            index.insert(new float[]{1});
        });
        assertThrows(IllegalArgumentException.class, () -> {
            index.insert(new float[]{1, 2, 3});
        });
    }

    @Test
    void expectIndexCreated() {
        var config = HnswConfig.builder().build();
        var index = new HnswIndex(config);
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
