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

package es.nachobrito.vulcanodb.core.domain.model.query.similarity;

/**
 * @author nacho
 */
public class CosineSimilarity implements VectorSimilarity {
    private static final float EPSILON = 1e-8f;

    @Override
    public double between(double[] vector1, double[] vector2) {

        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors are not the same size");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            normA += vector1[i] * vector1[i];
            normB += vector2[i] * vector2[i];
        }

        // Avoid division by zero.
        return dotProduct / Math.max(Math.sqrt(normA) * Math.sqrt(normB), EPSILON);
    }
}
