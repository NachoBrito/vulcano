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

package es.nachobrito.vulcanodb.core.domain.model.store.hnsw;

import es.nachobrito.vulcanodb.core.domain.model.query.similarity.CosineSimilarity;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;

/**
 * @author nacho
 */
public record HnswConfig(

        //The vector similarity function used to calculate distances
        VectorSimilarity vectorSimilarity,
        //The page size
        int blockSize,
        //The stored vector dimensions
        int dimensions,

        int efConstruction,// Size of the dynamic candidate list during insertion (e.g., 100).
        int m, //  Max number of connections per element (e.g., 16).
        int mMax, // Max connections per element per layer (usually M).
        int mMax0, // Max connections for Layer 0 (recommended 2 * M)
        int mL // - Level normalization factor, optimal is 1 / ln(M).
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VectorSimilarity vectorSimilarity = new CosineSimilarity();
        private int blockSize = 1_048_576;
        private int dimensions = 364;

        private int efConstruction = 100;
        private int m = 16;
        private int mMax = m;
        private int mMax0 = 2 * m;
        private int mL = (int) (1 / Math.log(m));

        public Builder withVectorSimilarity(VectorSimilarity vectorSimilarity) {
            this.vectorSimilarity = vectorSimilarity;
            return this;
        }

        public Builder withBlockSize(int blockSize) {
            this.blockSize = blockSize;
            return this;
        }

        public Builder withDimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder withEfConstruction(int efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        public Builder withM(int m) {
            this.m = m;
            return this;
        }

        public Builder withMMax(int mMax) {
            this.mMax = mMax;
            return this;
        }

        public Builder withMMax0(int mMax0) {
            this.mMax0 = mMax0;
            return this;
        }

        public Builder withML(int mL) {
            this.mL = mL;
            return this;
        }

        public HnswConfig build() {
            return new HnswConfig(vectorSimilarity, blockSize, dimensions, efConstruction, m, mMax, mMax0, mL);
        }
    }
}
