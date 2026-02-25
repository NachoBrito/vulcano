/*
 *    Copyright 2026 Nacho Brito
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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Manages a single memory-mapped segment file.
 *
 * @author nacho
 */
public final class SegmentManager implements AutoCloseable {

    private final Path path;
    private final Arena arena;
    private final MemorySegment segment;
    private final long size;
    private final int id;

    private final BitSet allocationBitmap;

    /**
     * Free log to defer frees until the end of the epoch.
     */
    private final List<DeferredFree> freeLog = new ArrayList<>();

    private record DeferredFree(long offset, long epoch) {}

    public SegmentManager(Path path, int id, long size) throws IOException {
        this.path = path;
        this.id = id;
        this.size = size;
        this.arena = Arena.ofShared();

        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            this.segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, size, arena);
        }

        int numPages = (int) (size / Layout.PAGE_SIZE);
        this.allocationBitmap = new BitSet(numPages);

        if (id == 0) {
            // Reserve page 0 for Superblock in the first segment
            allocationBitmap.set(0);
        }
    }

    public synchronized long allocatePage() {
        int pageIndex = allocationBitmap.nextClearBit(0);
        if ((long) pageIndex * Layout.PAGE_SIZE >= size) {
            return -1; // Segment full
        }
        allocationBitmap.set(pageIndex);
        return (long) pageIndex * Layout.PAGE_SIZE;
    }

    public synchronized void deferFree(long offset, long epoch) {
        freeLog.add(new DeferredFree(offset, epoch));
    }

    public synchronized void processFreeLog(long committedEpoch) {
        freeLog.removeIf(deferredFree -> {
            if (deferredFree.epoch <= committedEpoch) {
                int pageIndex = (int) (deferredFree.offset / Layout.PAGE_SIZE);
                allocationBitmap.clear(pageIndex);
                return true;
            }
            return false;
        });
    }

    public MemorySegment getPage(long offset) {
        return segment.asSlice(offset, Layout.PAGE_SIZE);
    }

    public MemorySegment getFullSegment() {
        return segment;
    }

    public void fsync() {
        segment.force();
    }

    @Override
    public void close() {
        arena.close();
    }

    public long size() {
        return size;
    }

    public int id() {
        return id;
    }
}
