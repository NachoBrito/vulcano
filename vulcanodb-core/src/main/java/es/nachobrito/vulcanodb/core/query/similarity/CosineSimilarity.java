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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * @author nacho
 */
public final class CosineSimilarity implements VectorSimilarity {
    private static final float EPSILON = 1e-8f;

    @Override
    public float between(float[] vector1, float[] vector2) {

        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors are not the same size");
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            normA += vector1[i] * vector1[i];
            normB += vector2[i] * vector2[i];
        }

        // Avoid division by zero.
        return dotProduct / (float) Math.max(Math.sqrt(normA) * Math.sqrt(normB), EPSILON);
    }

    @Override
    public float between(MemorySegment segment, long offset, float[] vector) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < vector.length; i++) {
            float v1 = segment.get(ValueLayout.JAVA_FLOAT_UNALIGNED, offset + (long) i * Float.BYTES);
            float v2 = vector[i];
            dotProduct += v1 * v2;
            normA += v1 * v1;
            normB += v2 * v2;
        }

        // Avoid division by zero.
        return dotProduct / (float) Math.max(Math.sqrt(normA) * Math.sqrt(normB), EPSILON);
    }

    @Override
    public float between(MemorySegment s1, long offset1, MemorySegment s2, long offset2, int dimensions) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < dimensions; i++) {
            float v1 = s1.get(ValueLayout.JAVA_FLOAT_UNALIGNED, offset1 + (long) i * Float.BYTES);
            float v2 = s2.get(ValueLayout.JAVA_FLOAT_UNALIGNED, offset2 + (long) i * Float.BYTES);
            dotProduct += v1 * v2;
            normA += v1 * v1;
            normB += v2 * v2;
        }

        // Avoid division by zero.
        return dotProduct / (float) Math.max(Math.sqrt(normA) * Math.sqrt(normB), EPSILON);
    }
}
