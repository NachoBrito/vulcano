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


import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Vector index that uses Chunking to scale. Vector components are stored in off-heap memory segments.
 * <br>
 * Thread safety:
 * This class should be safe for multiple threads access.
 *
 * @author nacho
 */
final class PagedVectorIndex {
    // List of memory segments (pages)
    private final List<MemorySegment> pages = new ArrayList<>();
    private final AtomicLong currentCount = new AtomicLong(0);
    private final int blockSize;
    private final int dimensions;

    /**
     *
     * @param blockSize  how many vectors per page
     * @param dimensions how many elements per vector
     */
    public PagedVectorIndex(int blockSize, int dimensions) {
        this.blockSize = blockSize;
        this.dimensions = dimensions;

        if (blockSize < 1) {
            throw new IllegalArgumentException("Invalid block size, must be > 0");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("Invalid dimensions, must be > 0");
        }
        // Start with one page
        addPage();
    }

    private void addPage() {
        // Allocate off-heap memory for 1 block of vectors
        long byteSize = (long) blockSize * dimensions * Float.BYTES;
        MemorySegment newPage = Arena.ofAuto().allocate(byteSize);
        pages.add(newPage);
    }

    /**
     * Adds a vector to this index, returning its absolute index
     *
     * @param vector the vector to add.
     * @return the position of the new vector in the index.
     */
    public long addVector(float[] vector) {
        long internalId = currentCount.getAndIncrement(); // Atomic, gives unique ID

        // 1. Check if we need a new page
        if (internalId % blockSize == 0 && internalId > 0) {
            // Synchronize only the slow, array-modifying operation
            synchronized (this) {
                // Double-check lock: check again in case another thread already added the page
                if (pages.size() <= internalId / blockSize) {
                    addPage();
                }
            }
        }

        // 2. Calculate location
        int pageIdx = (int) (internalId / blockSize);
        long offset = (internalId % blockSize) * dimensions * Float.BYTES;

        // 3. Write data (MemorySegment is generally thread-safe for disjoint writes)
        MemorySegment.copy(vector, 0, pages.get(pageIdx), ValueLayout.JAVA_FLOAT, offset, dimensions);

        return internalId;
    }

    /**
     * Returns the position dimIndex of the vector with the given id
     *
     * @param id       the vector id (as returned by the {@link #addVector(float[])} method
     * @param dimIndex the position within the vector
     * @return the value of the requested position in the vector
     */
    public float getElement(long id, int dimIndex) {
        if (dimIndex < 0 || dimIndex >= dimensions) {
            throw new IllegalArgumentException("Illegal dimension index");
        }
        if (id < 0 || id >= currentCount.get()) {
            throw new IllegalArgumentException("Illegal vector id");
        }

        int pageIdx = (int) (id / blockSize);

        long pageOffset = (id % blockSize) * dimensions * Float.BYTES;
        long finalOffset = pageOffset + ((long) dimIndex * Float.BYTES);

        return pages.get(pageIdx).get(ValueLayout.JAVA_FLOAT, finalOffset);
    }

    /**
     * Returns the vector corresponding to the given Id
     *
     * @param id the id to retrieve
     * @return the vector with given Id
     */
    public float[] getVector(long id) {
        if (id < 0 || id >= currentCount.get()) {
            throw new IllegalArgumentException("Illegal vector id");
        }
        int pageIdx = (int) (id / blockSize);
        long pageOffset = (id % blockSize) * dimensions * Float.BYTES;
        var sliceSize = dimensions * Float.BYTES;

        var slice = pages.get(pageIdx).asSlice(pageOffset, sliceSize);
        return slice.toArray(ValueLayout.JAVA_FLOAT);
    }

    /**
     *
     * @return the number of pages, for testing purposes.
     */
    int getPageCount() {
        return this.pages.size();
    }

    long getVectorCount() {
        return this.currentCount.get();
    }
}
