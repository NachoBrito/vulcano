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


import es.nachobrito.vulcanodb.core.query.similarity.VectorSimilarity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Vector index that uses Chunking to scale. Vector components are stored in off-heap memory segments, backed by
 * memory-mapped files.
 * <br>
 * Thread safety:
 * This class should be safe for multiple threads access.
 *
 * @author nacho
 */
final class PagedVectorIndex implements AutoCloseable {
    // List of memory segments (pages)
    private final List<MemorySegment> pages = new ArrayList<>();
    private final List<Arena> arenas = new ArrayList<>();
    private final AtomicLong currentCount = new AtomicLong(0);
    private final int blockSize;
    private final int dimensions;
    private final Path basePath;
    private final ReentrantLock expansionLock = new ReentrantLock();

    /**
     * Creates a paged vector index backed by memory-mapped files.
     *
     * @param blockSize  how many vectors per page
     * @param dimensions how many elements per vector
     * @param basePath   the base directory for persistence
     */
    public PagedVectorIndex(int blockSize, int dimensions, Path basePath) {
        this.blockSize = blockSize;
        this.dimensions = dimensions;
        this.basePath = basePath;

        if (blockSize < 1) {
            throw new IllegalArgumentException("Invalid block size, must be > 0");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("Invalid dimensions, must be > 0");
        }

        try {
            Files.createDirectories(basePath);
            loadExistingPages();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadExistingPages() throws IOException {
        int pageIdx = 0;
        while (true) {
            Path pagePath = basePath.resolve("vector-page-" + pageIdx + ".dat");
            if (!Files.exists(pagePath)) {
                break;
            }
            openPage(pageIdx, pagePath);
            pageIdx++;
        }
        if (pageIdx > 0) {
            currentCount.set((long) pageIdx * blockSize);
        } else {
            addPage();
        }
    }

    private void addPage() {
        int pageIdx = pages.size();
        Path pagePath = basePath.resolve("vector-page-" + pageIdx + ".dat");
        try {
            openPage(pageIdx, pagePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void openPage(int pageIdx, Path path) throws IOException {
        long byteSize = (long) blockSize * dimensions * Float.BYTES;
        FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (ch.size() < byteSize) {
            ch.truncate(byteSize);
        }
        Arena arena = Arena.ofShared();
        MemorySegment page = ch.map(FileChannel.MapMode.READ_WRITE, 0, byteSize, arena);
        arenas.add(arena);
        pages.add(page);
    }

    /**
     * Sets the actual vector count, used during recovery/loading.
     */
    void setVectorCount(long count) {
        this.currentCount.set(count);
        ensureCapacity(count > 0 ? count - 1 : 0);
    }

    private void ensureCapacity(long vectorId) {
        int requiredPages = (int) (vectorId / blockSize) + 1;
        if (pages.size() < requiredPages) {
            expansionLock.lock();
            try {
                while (pages.size() < requiredPages) {
                    addPage();
                }
            } finally {
                expansionLock.unlock();
            }
        }
    }

    /**
     * Adds a vector to this index, returning its absolute index
     *
     * @param vector the vector to add.
     * @return the position of the new vector in the index.
     */
    public long addVector(float[] vector) {
        long internalId = currentCount.getAndIncrement(); // Atomic, gives unique ID
        ensureCapacity(internalId);

        // 2. Calculate location
        int pageIdx = (int) (internalId / blockSize);
        long offset = (internalId % blockSize) * dimensions * Float.BYTES;

        // 3. Write data
        MemorySegment.copy(vector, 0, pages.get(pageIdx), ValueLayout.JAVA_FLOAT, offset, dimensions);

        return internalId;
    }

    /**
     * Calculates the similarity between a stored vector and a provided float array, without materializing the stored
     * vector.
     *
     * @param id         the vector id
     * @param vector     the vector to compare with
     * @param similarity the similarity metric to use
     * @return the similarity score
     */
    public float similarity(long id, float[] vector, VectorSimilarity similarity) {
        if (id < 0 || id >= currentCount.get()) {
            throw new IllegalArgumentException("Illegal vector id");
        }
        int pageIdx = (int) (id / blockSize);
        long pageOffset = (id % blockSize) * dimensions * Float.BYTES;

        return similarity.between(pages.get(pageIdx), pageOffset, vector);
    }

    /**
     * Calculates the similarity between two stored vectors, without materializing them.
     *
     * @param id1        the first vector id
     * @param id2        the second vector id
     * @param similarity the similarity metric
     * @return the similarity score
     */
    public float similarity(long id1, long id2, VectorSimilarity similarity) {
        if (id1 < 0 || id1 >= currentCount.get() || id2 < 0 || id2 >= currentCount.get()) {
            throw new IllegalArgumentException("Illegal vector id");
        }

        int p1 = (int) (id1 / blockSize);
        long o1 = (id1 % blockSize) * dimensions * Float.BYTES;

        int p2 = (int) (id2 / blockSize);
        long o2 = (id2 % blockSize) * dimensions * Float.BYTES;

        return similarity.between(pages.get(p1), o1, pages.get(p2), o2, dimensions);
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

        float[] result = new float[dimensions];
        MemorySegment.copy(pages.get(pageIdx), ValueLayout.JAVA_FLOAT, pageOffset, result, 0, dimensions);
        return result;
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

    @Override
    public void close() {
        for (Arena arena : arenas) {
            if (arena.scope().isAlive()) {
                arena.close();
            }
        }
    }
}
