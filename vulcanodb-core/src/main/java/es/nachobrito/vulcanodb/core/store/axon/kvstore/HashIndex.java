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

package es.nachobrito.vulcanodb.core.store.axon.kvstore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    private boolean initialized = false;

    @SuppressWarnings("unchecked")
    HashIndex(
            Path basePath,
            int bucketCount,
            long segmentSize,
            long committedIndexOffset,
            DataLog dataLog
    ) throws IOException {
        this.basePath = basePath;
        this.bucketCount = bucketCount;
        this.segmentSize = segmentSize;
        this.dataLog = dataLog;

        this.segments = new List[bucketCount];
        this.reserved = new AtomicLong[bucketCount];
        this.committed = new AtomicLong[bucketCount];

        recoverIndexOffsets(
                basePath,
                bucketCount,
                committedIndexOffset
        );

    }

    /**
     * Recovers the persisted state.
     *
     * @param indexDir                   the path where the files are stored
     * @param bucketCount                the number of buckets
     * @param globalCommittedIndexOffset the global offset
     * @throws IOException if the data cannot be read
     */
    private void recoverIndexOffsets(
            Path indexDir,
            int bucketCount,
            long globalCommittedIndexOffset
    ) throws IOException {
        if (this.initialized) {
            throw new IllegalStateException("Do not call recoverIndexOffsets if the instance is already initialized!");
        }
        if (!Files.exists(indexDir)) {
            for (int i = 0; i < bucketCount; i++) {
                reserved[i] = new AtomicLong(0);
                committed[i] = new AtomicLong(0);
            }
            return; // all zeros
        }

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            segments[bucket] = new ArrayList<>();

            long offset = 0;
            long remaining = globalCommittedIndexOffset;
            int segmentIndex = 0;

            while (remaining > 0) {
                Path segPath = getSetmentPath(bucket, segmentIndex);
                if (!Files.exists(segPath)) {
                    break;
                }
                var segment = getOrCreateSegment(bucket, segmentIndex);
                var channel = segment.channel();

                long segSize = channel.size();
                long scanLimit = Math.min(segSize, remaining);

                Arena arena = Arena.ofConfined();
                MemorySegment seg =
                        channel.map(FileChannel.MapMode.READ_ONLY, 0, segSize, arena);
                long p = 0;
                while (p + 4 <= scanLimit) {
                    int entryLen = seg.get(ValueLayout.JAVA_INT, p);
                    if (entryLen <= 0) {
                        break;
                    }
                    if (p + entryLen > scanLimit) {
                        break;
                    }
                    p += entryLen;
                }
                offset += p;
                remaining -= p;

                segmentIndex++;
            }
            reserved[bucket] = new AtomicLong(offset);
            committed[bucket] = new AtomicLong(offset);
        }
        initialized = true;
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
        long segmentIndex = globalOffset / segmentSize;
        return getOrCreateSegment(bucket, segmentIndex);
    }

    /**
     * Gets the corresponding segment inside the provided bucket. Will create all the required segments up to the
     * given index.
     *
     * @param bucket       the bucket index
     * @param segmentIndex the segment index
     * @return the (maybe new) Segment
     */
    private Segment getOrCreateSegment(int bucket, long segmentIndex) {
        List<Segment> list = segments[bucket];
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
            Path path = getSetmentPath(bucket, index);

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

    private Path getSetmentPath(int bucket, long index) {
        return basePath.resolve(
                "index-b" + bucket + "-seg" + index + ".idx");
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

    /**
     *
     * @return a Stream of stored data offsets
     */
    public Stream<Long> valueOffsets() {
        var spliterator = new LiveOffsetSpliterator(segments, 0, bucketCount);

        return StreamSupport.stream(spliterator, false);
    }

    // ======================================================
    // Per-bucket deduplicating spliterator (current live set)
    // ======================================================

    private static final class LiveOffsetSpliterator implements Spliterator<Long> {

        private final List<Segment>[] buckets;
        private int startBucket;
        private final int endBucket;

        // iterator state for emission
        private Iterator<Long> currentBucketOffsets = null;

        LiveOffsetSpliterator(List<Segment>[] buckets, int startBucket, int endBucket) {
            this.buckets = buckets;
            this.startBucket = startBucket;
            this.endBucket = endBucket;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Long> action) {

            while (true) {

                // if we are currently emitting a bucket result
                if (currentBucketOffsets != null && currentBucketOffsets.hasNext()) {
                    action.accept(currentBucketOffsets.next());
                    return true;
                }

                // finished emitting -> reset and move to next bucket
                currentBucketOffsets = null;

                if (startBucket >= endBucket) {
                    return false;
                }

                List<Segment> bucket = buckets[startBucket++];
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }

                // build latest-version map for this bucket
                Map<KeyView, Long> lastOffsets = new HashMap<>();

                for (Segment seg : bucket) {

                    MemorySegment m = seg.memory();
                    long size = m.byteSize();
                    long pos = 0;

                    while (pos + 8 <= size) {

                        int entryLen = m.get(INT, pos);
                        if (entryLen == 0) {
                            break; // end of committed data in segment
                        }

                        int keyLen = m.get(INT, pos + 4);

                        long keyStart = pos + 8;
                        long keyEnd = keyStart + keyLen;

                        long aligned = align8(keyEnd);
                        long dataOffset = m.get(LONG, aligned);

                        // skip tombstones

                        // key is deduped by content, not object identity
                        KeyView key = new KeyView(m, keyStart, keyLen);
                        lastOffsets.put(key, dataOffset);

                        pos += entryLen; // move to next entry
                    }
                }

                // now emit only latest offsets (removing deleted entries that have negative offset)
                currentBucketOffsets = lastOffsets
                        .values()
                        .stream()
                        .filter(it -> it >= 0)
                        .iterator();
            }
        }

        @Override
        public Spliterator<Long> trySplit() {
            int remaining = endBucket - startBucket;
            if (remaining <= 1) {
                return null;
            }

            int mid = startBucket + remaining / 2;

            LiveOffsetSpliterator split =
                    new LiveOffsetSpliterator(buckets, startBucket, mid);

            this.startBucket = mid;
            this.currentBucketOffsets = null;

            return split;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return NONNULL | DISTINCT | IMMUTABLE;
        }

        private static long align8(long v) {
            long r = v & 7L;
            return (r == 0) ? v : (v + (8 - r));
        }
    }

    // =============================
    // Lightweight key view wrapper
    // =============================

    /**
     * Zero-copy equality + hash based on key bytes in a segment.
     * This allows dedup without materializing Strings or arrays.
     */
    private record KeyView(MemorySegment segment, long position, int length) {

        @Override
        public int hashCode() {
            int h = 1;
            for (int i = 0; i < length; i++) {
                byte b = segment.get(ValueLayout.JAVA_BYTE, position + i);
                h = 31 * h + b;
            }
            return h;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof KeyView otherKeyView) || otherKeyView.length != length) {
                return false;
            }

            for (int i = 0; i < length; i++) {
                byte a = segment.get(ValueLayout.JAVA_BYTE, position + i);
                byte b = otherKeyView.segment.get(ValueLayout.JAVA_BYTE, otherKeyView.position + i);
                if (a != b) return false;
            }
            return true;
        }
    }
}
