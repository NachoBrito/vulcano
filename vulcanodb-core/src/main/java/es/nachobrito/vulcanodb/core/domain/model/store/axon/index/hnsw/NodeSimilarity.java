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

/**
 * @author nacho
 */
public record NodeSimilarity(long vectorId, float similarity) implements Comparable<NodeSimilarity> {


    @Override
    public int compareTo(NodeSimilarity other) {
        // We implement natural ordering for the Min-Heap (Candidates).
        // Max similarity should be considered the 'smallest' (highest priority).
        // i.e., larger 'similarity' means higher priority (comes first).
        return Float.compare(other.similarity, this.similarity); // Reversed comparison
    }
}
