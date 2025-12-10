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

/**
 * <pre>
 * [ Node 0 Data ........................ ] [ ... ]
 * | Count (int)  | N1 | N2 | ... | N_max | | ... |
 * </pre>
 *
 * @author nacho
 */
public class PagedGraphIndex {
    // Max neighbors for Layer 0 (usually 2 * M)
    private final int maxConnections;

    // Size of one node's connection data in bytes:
    // 4 bytes (count) + (mMax0 * 4 bytes)
    private final int slotSizeBytes;

    private final int blockSize; // Nodes per page
    private final List<MemorySegment> pages = new ArrayList<>();

    public PagedGraphIndex(int maxConnections, int blockSize) {
        this.maxConnections = maxConnections;
        this.slotSizeBytes = Integer.BYTES + (maxConnections * Long.BYTES);
        this.blockSize = blockSize;
        addPage();
    }

    private void addPage() {
        long totalBytes = (long) blockSize * slotSizeBytes;
        pages.add(Arena.ofAuto().allocate(totalBytes));
    }

    private void ensureCapacity(long vectorId) {
        int pageIdx = (int) (vectorId / blockSize);
        while (pages.size() <= pageIdx) {
            addPage();
        }
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
        page.set(ValueLayout.JAVA_INT, pageStart, neighbors.length);

        // 3. Write Neighbors
        // We skip 4 bytes (the count) to find the start of the neighbor array
        long neighborArrayOffset = pageStart + Integer.BYTES;

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
        int count = page.get(ValueLayout.JAVA_INT, pageStart);
        var buffer = new long[count];

        // 2. Read Neighbors
        long neighborArrayOffset = pageStart + Integer.BYTES;
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
        int count = page.get(ValueLayout.JAVA_INT, pageStart);

        // 2. Read Neighbors
        long neighborArrayOffset = pageStart + Integer.BYTES;
        MemorySegment.copy(
                page, neighborArrayOffset,
                MemorySegment.ofArray(outputBuffer), 0,
                (long) count * Long.BYTES
        );

        return count;
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
        int count = page.get(ValueLayout.JAVA_INT, pageStart);
        if (count >= maxConnections) {
            throw new IllegalArgumentException("Too many neighbors");
        }

        // 2. save new connection
        // at page start + count size + size of current connections
        long offset = pageStart + Integer.BYTES + (long) count * Long.BYTES;
        page.set(ValueLayout.JAVA_LONG, offset, vectorId2);

        // 3. update count
        page.set(ValueLayout.JAVA_INT, pageStart, count + 1);
    }
}
