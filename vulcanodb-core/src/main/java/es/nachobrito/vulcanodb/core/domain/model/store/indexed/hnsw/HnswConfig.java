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

package es.nachobrito.vulcanodb.core.domain.model.store.indexed.hnsw;

import es.nachobrito.vulcanodb.core.domain.model.query.similarity.CosineSimilarity;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;

/**
 * @author nacho
 */
public record HnswConfig(

        VectorSimilarity vectorSimilarity,
        int blockSize,
        int dimensions,
        int efConstruction,

        int efSearch,
        int m,
        int mMax,
        int mMax0,
        float mL
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VectorSimilarity vectorSimilarity = new CosineSimilarity();
        private int blockSize = 1_048_576;
        private int dimensions = 384;

        private int efConstruction = 100;
        private int efSearch = 50;
        private int m = 16;
        private int mMax = m;
        private int mMax0 = 2 * m;
        private float mL = (float) (1.0 / Math.log(m));

        /**
         * The similarity function used to compare vectors
         *
         * @param vectorSimilarity the vector similarity function
         * @return this builder
         */
        public Builder withVectorSimilarity(VectorSimilarity vectorSimilarity) {
            this.vectorSimilarity = vectorSimilarity;
            return this;
        }

        /**
         * The block size for memory pagination. HnswIndex uses {@link java.lang.foreign.MemorySegment}s to store raw
         * vector data, and vector connections.
         *
         * @param blockSize the block size for pagination.
         * @return this builder
         */
        public Builder withBlockSize(int blockSize) {
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Number of dimensions in the stored vectors.
         *
         * @param dimensions the vector dimensions
         * @return this builder
         */
        public Builder withDimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * efConstruction (Index Building Quality)Goal: Maximize the quality and accuracy of the connections when
         * a new node is inserted. This ensures the graph is robust, navigable, and avoids getting trapped in local
         * minima during future searches.
         * <p>
         * Cost: This process happens once when the index is built. The cost is high build time and high CPU usage
         * during construction.
         * <p>
         * Recommended Value: This parameter is typically set to a high value (e.g., 100 to 500). The higher the value,
         * the more thoroughly the algorithm explores the neighborhood of the new point, resulting in better graph
         * topology and higher search recall.
         * <p>
         * Relationship to m: efConstruction should generally be significantly  larger than the maximum link
         * count m (e.g., if M=16, efConstruction might be 200).
         *
         * @param efConstruction the efConstruction value
         * @return this builder
         */
        public Builder withEfConstruction(int efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        /**
         * efSearch (Query Speed/Accuracy Trade-Off)5Goal: Balance the speed of the search query (latency) with the desired
         * accuracy (recall). This parameter controls the exploration depth at the moment of the query.
         * <p>
         * Cost: This process happens every single time a search query is executed. The cost is high query latency.
         * <p>
         * Recommended Value: This parameter is typically set to a lower value (e.g., 50 to 200) and is often dynamically
         * adjusted at runtime based on the specific use case.
         * - If you need real-time latency (e.g., autocomplete), you use a small efSearch (e.g., 50).
         * - If you need maximum possible accuracy (e.g., a critical batch job), you use a large efSearch (e.g., 500).
         * <p>
         * Relationship to K: efSearch must be at least greater than the number of results you want to return (K)
         *
         * @param efSearch the value for efSearch
         * @return this builder
         */
        public Builder withEfSearch(int efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        /**
         * Max number of connections per element (e.g., 16).
         *
         * @param m the max number of connections per element
         * @return this builder
         */
        public Builder withM(int m) {
            this.m = m;
            return this;
        }

        /**
         * Max connections per element per layer (usually M).
         *
         * @param mMax max connections per element per layer
         * @return this builder
         */
        public Builder withMMax(int mMax) {
            this.mMax = mMax;
            return this;
        }

        /**
         * Max connections for Layer 0 (recommended 2 * M)
         *
         * @param mMax0 Max connections for Layer 0
         * @return this builder
         */
        public Builder withMMax0(int mMax0) {
            this.mMax0 = mMax0;
            return this;
        }

        /**
         * Level normalization factor, optimal is 1 / ln(M).
         *
         * @param mL level of normalization factor
         * @return this builder
         */
        public Builder withML(int mL) {
            this.mL = mL;
            return this;
        }

        /**
         *
         * @return a new instance of HnswConfig with the defined values.
         */
        public HnswConfig build() {
            return new HnswConfig(vectorSimilarity, blockSize, dimensions, efConstruction, efSearch, m, mMax, mMax0, mL);
        }


    }
}
