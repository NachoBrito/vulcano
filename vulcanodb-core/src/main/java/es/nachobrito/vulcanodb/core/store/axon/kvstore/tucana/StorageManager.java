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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages multiple memory-mapped segments with asynchronous pre-allocation.
 *
 * @author nacho
 */
public final class StorageManager implements AutoCloseable {

    private final Path baseDir;
    private final long segmentSize;
    private final List<SegmentManager> segments = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong currentEpoch = new AtomicLong(0);

    /**
     * Pool of pre-allocated segments to avoid latency during switch.
     */
    private final BlockingQueue<SegmentManager> preAllocatedSegments = new LinkedBlockingQueue<>(2);

    public StorageManager(Path baseDir, long segmentSize) throws IOException {
        this.baseDir = baseDir;
        this.segmentSize = segmentSize;
        Files.createDirectories(baseDir);

        // Load existing segments
        int id = 0;
        while (Files.exists(segmentPath(id))) {
            segments.add(new SegmentManager(segmentPath(id), id, segmentSize));
            id++;
        }

        if (segments.isEmpty()) {
            segments.add(new SegmentManager(segmentPath(0), 0, segmentSize));
        }

        // Start pre-allocation background task
        triggerPreAllocation();
    }

    private Path segmentPath(int id) {
        return baseDir.resolve("segment_" + id + ".seg");
    }

    public SegmentManager getSegment(int id) {
        lock.readLock().lock();
        try {
            return segments.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public SegmentManager getSegmentForOffset(long globalOffset) {
        int segmentId = (int) (globalOffset / segmentSize);
        return getSegment(segmentId);
    }

    public long localOffset(long globalOffset) {
        return globalOffset % segmentSize;
    }

    private void triggerPreAllocation() {
        CompletableFuture.runAsync(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int nextId;
                    lock.readLock().lock();
                    try {
                        nextId = segments.size() + preAllocatedSegments.size();
                    } finally {
                        lock.readLock().unlock();
                    }
                    
                    SegmentManager segment = new SegmentManager(segmentPath(nextId), nextId, segmentSize);
                    preAllocatedSegments.put(segment);
                }
            } catch (Exception e) {
                // Background pre-allocation stopped
            }
        });
    }

    public synchronized long allocatePage() throws IOException {
        SegmentManager lastSegment = segments.get(segments.size() - 1);
        long localOffset = lastSegment.allocatePage();
        if (localOffset != -1) {
            return (long) lastSegment.id() * segmentSize + localOffset;
        }

        // Segment full, switch to pre-allocated one
        lock.writeLock().lock();
        try {
            SegmentManager nextSegment = preAllocatedSegments.poll();
            if (nextSegment == null) {
                // Fallback to synchronous allocation if pool is empty
                int newId = segments.size();
                nextSegment = new SegmentManager(segmentPath(newId), newId, segmentSize);
            }
            segments.add(nextSegment);
            return (long) nextSegment.id() * segmentSize + nextSegment.allocatePage();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deferFree(long globalOffset, long epoch) {
        getSegmentForOffset(globalOffset).deferFree(localOffset(globalOffset), epoch);
    }

    public void processFreeLog(long committedEpoch) {
        lock.readLock().lock();
        try {
            for (SegmentManager segment : segments) {
                segment.processFreeLog(committedEpoch);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public long currentEpoch() {
        return currentEpoch.get();
    }

    public void incrementEpoch() {
        currentEpoch.incrementAndGet();
    }

    public void fsyncAll() {
        lock.readLock().lock();
        try {
            for (SegmentManager segment : segments) {
                segment.fsync();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public long totalSize() {
        lock.readLock().lock();
        try {
            return (long) segments.size() * segmentSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            for (SegmentManager segment : segments) {
                segment.close();
            }
            SegmentManager preAlloc;
            while ((preAlloc = preAllocatedSegments.poll()) != null) {
                preAlloc.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
