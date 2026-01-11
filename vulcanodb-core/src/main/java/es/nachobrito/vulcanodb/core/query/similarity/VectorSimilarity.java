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
public interface VectorSimilarity {

    static VectorSimilarity getDefault() {
        return new CosineSimilarity();
    }

    float between(float[] vector1, float[] vector2);

    /**
     * Calculates the similarity between a memory segment containing a vector and a float array.
     * This is an optimization to avoid materializing the vector from the segment.
     *
     * @param segment the memory segment containing the vector
     * @param offset  the offset in the segment where the vector starts
     * @param vector  the float array
     * @return the similarity score
     */
    float between(MemorySegment segment, long offset, float[] vector);

    /**
     * Calculates the similarity between two memory segments containing vectors.
     * This is an optimization to avoid materializing the vectors from the segments.
     *
     * @param s1         the first memory segment
     * @param offset1    the offset in the first segment
     * @param s2         the second memory segment
     * @param offset2    the offset in the second segment
     * @param dimensions the number of dimensions of the vectors
     * @return the similarity score
     */
    float between(MemorySegment s1, long offset1, MemorySegment s2, long offset2, int dimensions);
}
