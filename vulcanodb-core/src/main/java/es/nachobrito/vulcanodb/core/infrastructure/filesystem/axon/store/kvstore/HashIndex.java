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

package es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.store.kvstore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author nacho
 */
final class HashIndex implements AutoCloseable {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;

    private final Path basePath;
    private final int bucketCount;
    private final long segmentSize;

    // Per-bucket segment lists
    private final List<Segment>[] segments;

    // Per-bucket offsets
    private final AtomicLong[] reserved;
    private final AtomicLong[] committed;

    private final DataLog dataLog;

    @SuppressWarnings("unchecked")
    HashIndex(
            Path basePath,
            int bucketCount,
            long segmentSize,
            long[] recoveredCommittedOffsets,
            DataLog dataLog
    ) {
        this.basePath = basePath;
        this.bucketCount = bucketCount;
        this.segmentSize = segmentSize;
        this.dataLog = dataLog;

        this.segments = new List[bucketCount];
        this.reserved = new AtomicLong[bucketCount];
        this.committed = new AtomicLong[bucketCount];

        for (int i = 0; i < bucketCount; i++) {
            segments[i] = new ArrayList<>();
            reserved[i] = new AtomicLong(recoveredCommittedOffsets[i]);
            committed[i] = new AtomicLong(recoveredCommittedOffsets[i]);
        }
    }

    void put(String key, long dataOffset) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        // entryLength + keyLength + keyBytes + dataOffset
        int rawSize = 4 + 4 + kb.length + 8;
        long entrySize = align(rawSize, 8);

        int b = bucket(key);

        long offset = reserved[b].getAndAdd(entrySize);
        long local = offset % segmentSize;

        Segment s = segmentFor(b, offset);
        MemorySegment m = s.memory();

        // Write payload first:

        //key length
        m.set(INT, local + 4, kb.length);
        //key bytes
        m.asSlice(local + 8, kb.length)
                .copyFrom(MemorySegment.ofArray(kb));

        //offset (align before writing the long)
        long dataOffsetPos = align(local + 8 + kb.length, 8);
        m.set(LONG, dataOffsetPos, dataOffset);

        // Publish entry
        VarHandle.releaseFence();
        m.set(INT, local, (int) entrySize);

        committed[b].accumulateAndGet(offset + entrySize, Math::max);
    }

    static long align(long v, long a) {
        return (v + a - 1) & ~(a - 1);
    }

    long get(String key) {
        byte[] target = key.getBytes(StandardCharsets.UTF_8);
        int bucket = bucket(key);

        long limit = committed[bucket].get();
        long p = 0;

        long result = -1;

        while (p < limit) {
            Segment s = segmentFor(bucket, p);
            MemorySegment m = s.memory();

            long local = p % segmentSize;

            int entryLen = m.get(INT, local);
            if (entryLen <= 0 || p + entryLen > limit) {
                break;
            }

            int keyLen = m.get(INT, local + 4);
            if (keyLen < 0 || keyLen > entryLen - 16) {
                throw new IllegalStateException("Corrupt index entry at " + p);
            }

            long keyPos = local + 8;
            byte[] kb = new byte[keyLen];

            MemorySegment.ofArray(kb)
                    .copyFrom(m.asSlice(keyPos, keyLen));

            if (Arrays.equals(kb, target)) {
                long dataOffsetPos = align(keyPos + keyLen, 8);

                if (dataOffsetPos + 8 > local + entryLen) {
                    throw new IllegalStateException("Corrupt index entry at " + p);
                }

                result = m.get(LONG, dataOffsetPos);
            }

            p += entryLen;
        }

        return result;
    }

    /**
     * HashMap-like collision handling: Keys are grouped into buckets. We select the bucket for a given key by getting
     * the reminder of (key hashcode) / (bucket count)
     *
     * @param k the key
     * @return the bucket index for this key
     */
    private int bucket(String k) {
        return (k.hashCode() & 0x7fffffff) % bucketCount;
    }

    /**
     * Segmented storage: each offset in a bucket belong in a fixed-size, memory mapped, file.
     * <p>
     * This method creates all the buckets up to the given offset.
     *
     * @param bucket       the bucket index
     * @param globalOffset the global offset
     * @return the corresponding Segment instance
     */
    private Segment segmentFor(int bucket, long globalOffset) {
        List<Segment> list = segments[bucket];
        long segmentIndex = globalOffset / segmentSize;

        if (segmentIndex < list.size()) {
            return list.get((int) segmentIndex);
        }

        synchronized (list) {
            while (segmentIndex >= list.size()) {
                list.add(createSegment(bucket, list.size()));
            }
            return list.get((int) segmentIndex);
        }
    }

    /**
     * Creates the segment (FileChannel, Arena, MemorySegment) for the given bucket, index values
     *
     * @param bucket the bucket index
     * @param index  the segment index
     * @return the new Segment
     */
    private Segment createSegment(int bucket, long index) {
        try {
            Path path = basePath.resolve(
                    "index-b" + bucket + "-seg" + index + ".idx");

            FileChannel ch = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );

            ch.truncate(segmentSize);

            Arena arena = Arena.ofShared();
            MemorySegment mem =
                    ch.map(FileChannel.MapMode.READ_WRITE, 0, segmentSize, arena);

            return new Segment(ch, arena, mem);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long committedOffset() {
        long max = 0;
        for (AtomicLong c : committed) {
            long v = c.get();
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    @Override
    public void close() throws Exception {
        for (List<Segment> bucket : segments) {
            for (Segment segment : bucket) {
                segment.close();
            }
            bucket.clear();
        }
    }
}
