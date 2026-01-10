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

package es.nachobrito.vulcanodb.core.query.similarity;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author nacho
 */
class CosineSimilarityTest {
    private final VectorSimilarity similarity = new CosineSimilarity();

    @Test
    void expectCosineDistance() {
        assertEquals(1.0, similarity.between(new float[]{0, 1}, new float[]{0, 1}));
        assertEquals(-1.0, similarity.between(new float[]{1, 1}, new float[]{-1, -1}));
        assertEquals(0.0, similarity.between(new float[]{0, 1}, new float[]{1, 0}));
        assertEquals(
                Math.round(100.0 * Math.cos(Math.PI / 4)),
                Math.round(100.0 * similarity.between(new float[]{1, 0}, new float[]{1, 1})));
    }

    @Test
    void expectExceptionIfDifferentSizes() {
        assertThrows(IllegalArgumentException.class, () -> {
            similarity.between(new float[]{0, 1}, new float[]{0, 0, 0});
        });
    }

    @Test
    void expectCorrectSimilarityFromMemorySegment() {
        float[] v1 = new float[]{1, 0, 1};
        float[] v2 = new float[]{1, 1, 0};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(v1.length * Float.BYTES);
            for (int i = 0; i < v1.length; i++) {
                segment.set(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES, v1[i]);
            }

            float expected = similarity.between(v1, v2);
            float actual = similarity.between(segment, 0, v2);

            assertEquals(expected, actual, 1e-6f);
        }
    }

    @Test
    void expectCorrectSimilarityWithOffset() {
        float[] v1 = new float[]{1, 2, 3};
        float[] v2 = new float[]{4, 5, 6};
        long offset = 100;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(offset + v1.length * Float.BYTES);
            for (int i = 0; i < v1.length; i++) {
                segment.set(ValueLayout.JAVA_FLOAT, offset + (long) i * Float.BYTES, v1[i]);
            }

            float expected = similarity.between(v1, v2);
            float actual = similarity.between(segment, offset, v2);

            assertEquals(expected, actual, 1e-6f);
        }
    }

}
