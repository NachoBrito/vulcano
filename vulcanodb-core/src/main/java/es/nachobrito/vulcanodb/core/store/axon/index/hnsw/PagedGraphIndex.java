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
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <pre>
 * [ Node 0 Data ........................ ] [ ... ]
 * | Count (long) | N1 | N2 | ... | N_max | | ... |
 * </pre>
 *
 * @author nacho
 */
final public class PagedGraphIndex implements AutoCloseable {
    // Max neighbors for Layer 0 (usually 2 * M)
    private final int maxConnections;

    // Size of one node's connection data in bytes:
    // 8 bytes (count) + (mMax0 * 8 bytes)
    // We use a long for count to ensure 8-byte alignment for the neighbors array
    private final int slotSizeBytes;

    private final int blockSize; // Nodes per page
    private final List<MemorySegment> pages = new ArrayList<>();
    private final List<Arena> arenas = new ArrayList<>();
    private final Path basePath;
    private final ReentrantLock expansionLock = new ReentrantLock();

    /**
     * Creates a paged graph index backed by memory-mapped files.
     *
     * @param maxConnections max neighbors per node
     * @param blockSize      nodes per page
     * @param basePath       the base directory for persistence
     */
    public PagedGraphIndex(int maxConnections, int blockSize, Path basePath) {
        this.maxConnections = maxConnections;
        this.slotSizeBytes = Long.BYTES + (maxConnections * Long.BYTES);
        this.blockSize = blockSize;
        this.basePath = basePath;

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
            Path pagePath = basePath.resolve("graph-page-" + pageIdx + ".dat");
            if (!Files.exists(pagePath)) {
                break;
            }
            openPage(pageIdx, pagePath);
            pageIdx++;
        }
        if (pageIdx == 0) {
            addPage();
        }
    }

    private void addPage() {
        int pageIdx = pages.size();
        Path pagePath = basePath.resolve("graph-page-" + pageIdx + ".dat");
        try {
            openPage(pageIdx, pagePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void openPage(int pageIdx, Path path) throws IOException {
        long totalBytes = (long) blockSize * slotSizeBytes;
        FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (ch.size() < totalBytes) {
            ch.truncate(totalBytes);
        }
        Arena arena = Arena.ofShared();
        MemorySegment page = ch.map(FileChannel.MapMode.READ_WRITE, 0, totalBytes, arena);
        arenas.add(arena);
        pages.add(page);
    }

    private void ensureCapacity(long vectorId) {
        int pageIdx = (int) (vectorId / blockSize);
        if (pages.size() <= pageIdx) {
            expansionLock.lock();
            try {
                while (pages.size() <= pageIdx) {
                    addPage();
                }
            } finally {
                expansionLock.unlock();
            }
        }
    }

    /**
     * Convenience method, to handle unboxing
     *
     * @param vectorId  the vectorId
     * @param neighbors the new connections
     */
    public void setConnections(long vectorId, Set<Long> neighbors) {
        var unboxedArray = neighbors.stream().mapToLong(it -> it).toArray();
        setConnections(vectorId, unboxedArray);
    }

    /**
     * Overwrites the connections for a specific node.
     * Used after the Heuristic (Algorithm 4) selects the best neighbors.
     */
    public void setConnections(long vectorId, long[] neighbors) {
        if (neighbors.length > maxConnections) {
            throw new IllegalArgumentException("Too many neighbors");
        }
        ensureCapacity(vectorId);

        // 1. Calculate Offsets
        int pageIdx = (int) (vectorId / blockSize);
        long pageStart = (vectorId % blockSize) * slotSizeBytes;
        MemorySegment page = pages.get(pageIdx);

        // 2. Write Count
        page.set(ValueLayout.JAVA_LONG, pageStart, (long) neighbors.length);

        // 3. Write Neighbors
        // We skip 8 bytes (the count) to find the start of the neighbor array
        long neighborArrayOffset = pageStart + Long.BYTES;

        MemorySegment.copy(
                MemorySegment.ofArray(neighbors), 0,
                page, neighborArrayOffset,
                (long) neighbors.length * Long.BYTES
        );
    }


    /**
     * Reads connections of a given vector
     *
     * @param vectorId the vector id
     * @return the connections array
     */
    public long[] getConnections(long vectorId) {
        if (vectorId < 0 || vectorId / blockSize >= pages.size()) return new long[0];

        int pageIdx = (int) (vectorId / blockSize);
        long pageStart = (vectorId % blockSize) * slotSizeBytes;
        MemorySegment page = pages.get(pageIdx);

        // 1. Read Count
        int count = (int) page.get(ValueLayout.JAVA_LONG, pageStart);
        if (count == 0) return new long[0];
        var buffer = new long[count];

        // 2. Read Neighbors
        long neighborArrayOffset = pageStart + Long.BYTES;
        MemorySegment.copy(
                page, neighborArrayOffset,
                MemorySegment.ofArray(buffer), 0,
                (long) count * Long.BYTES
        );

        return buffer;
    }

    /**
     * Reads connections into a reusable array.
     * Returns the number of neighbors found.
     */
    public long getConnections(long vectorId, long[] outputBuffer) {
        if (vectorId < 0 || vectorId / blockSize >= pages.size()) return 0;

        int pageIdx = (int) (vectorId / blockSize);
        long pageStart = (vectorId % blockSize) * slotSizeBytes;
        MemorySegment page = pages.get(pageIdx);

        // 1. Read Count
        int count = (int) page.get(ValueLayout.JAVA_LONG, pageStart);
        int toCopy = Math.min(count, outputBuffer.length);

        // 2. Read Neighbors
        long neighborArrayOffset = pageStart + Long.BYTES;
        MemorySegment.copy(
                page, neighborArrayOffset,
                MemorySegment.ofArray(outputBuffer), 0,
                (long) toCopy * Long.BYTES
        );

        return toCopy;
    }

    /**
     * Adds vector 2 as connection of vector 1
     *
     * @param vectorId1 id of vector 1
     * @param vectorId2 id of vector 2
     */
    public void addConnection(long vectorId1, long vectorId2) {
        ensureCapacity(vectorId1);
        int pageIdx = (int) (vectorId1 / blockSize);
        long pageStart = (vectorId1 % blockSize) * slotSizeBytes;
        MemorySegment page = pages.get(pageIdx);

        // 1. Read Count
        long count = page.get(ValueLayout.JAVA_LONG, pageStart);
        if (count >= maxConnections) {
            throw new IllegalArgumentException("Too many neighbors");
        }

        // 2. save new connection
        // at page start + count size + size of current connections
        long offset = pageStart + Long.BYTES + count * Long.BYTES;
        page.set(ValueLayout.JAVA_LONG, offset, vectorId2);

        // 3. update count
        page.set(ValueLayout.JAVA_LONG, pageStart, count + 1);
    }

    @Override
    public void close() {
        for (Arena arena : arenas) {
            if (arena.scope().isAlive()) {
                arena.close();
            }
        }
    }

    public long offHeapBytes() {
        return (long) pages.size() * blockSize * slotSizeBytes;
    }
}
