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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.appendonly;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes data in variable length entries. Uses VarHandle.releaseFence(); to avoid modern CPUs or the JVM to
 * reorder instructions so that the entry size is visible before the data is written.
 * <p>
 * General entry structure:
 * [int entryLen]
 * [int dataType]
 * [int keyLen]
 * ... other metadata fields
 * [key bytes]
 * [padding] <- alignment
 * [data bytes]
 * <p>
 * General data write pattern:
 * 1. Write meta fields (data type, key length, etc)
 * 2. Write key bytes
 * 3. Write data bytes <- aligned, can introduce padding
 * 4. Call VarHandle.releaseFence()
 * 5. Write entry length (at the beginning)
 *
 * @author nacho
 */
final class DataLog implements AutoCloseable {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;

    private final long segmentSize;
    private final List<Segment> segments = new ArrayList<>();
    private final AtomicLong reserved;
    private final AtomicLong committed;
    private final Path basePath;
    private final ReentrantLock segmentLock = new ReentrantLock();

    DataLog(Path base, long segmentSize, long recoveredOffset) {
        this.basePath = base;
        this.segmentSize = segmentSize;
        this.reserved = new AtomicLong(recoveredOffset);
        this.committed = new AtomicLong(recoveredOffset);
        ensureSegment(recoveredOffset / segmentSize);
    }

    private void ensureSegment(long idx) {
        while (segments.size() <= idx) {
            segments.add(createSegment(segments.size()));
        }
    }

    long writeString(String key, String value) {
        return writeBytes(key, value.getBytes(StandardCharsets.UTF_8), ValueType.STRING);
    }

    long writeBytes(String key, byte[] bytes) {
        return writeBytes(key, bytes, ValueType.BYTES);
    }

    private long writeBytes(String key, byte[] vb, ValueType type) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        // Calculate size including potential padding
        // To be thread-safe, we must reserve first.
        // But the padding depends on the actual offset we get!
        // Solution: reserve enough for the worst-case padding (7 bytes)
        // Or better: use a simpler approach if possible.
        // Given the constraints, let's keep the alignment but calculate it safely.

        int rawPayloadHeaderSize = 12 + kb.length;
        int maxEntrySize = (int) align(rawPayloadHeaderSize + 7 + vb.length, 8);

        long offset = reserved.getAndAdd(maxEntrySize);
        long p = offset % segmentSize;
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();

        long payloadOffset = p + 12 + kb.length;
        long alignedPayloadOffset = align(payloadOffset, 8);
        int internalPadding = (int) (alignedPayloadOffset - payloadOffset);

        int rawSize = 12 + kb.length + internalPadding + vb.length;

        m.set(INT, p + 4, type.id);
        m.set(INT, p + 8, kb.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 12, kb.length);

        MemorySegment.copy(MemorySegment.ofArray(vb), 0, m, alignedPayloadOffset, vb.length);

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + maxEntrySize, Math::max);
        return offset;
    }

    long writeInteger(String key, int value) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        int rawPayloadHeaderSize = 12 + kb.length;
        int maxEntrySize = (int) align(rawPayloadHeaderSize + 7 + 4, 8);

        long offset = reserved.getAndAdd(maxEntrySize);
        long p = offset % segmentSize;
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();

        long payloadOffset = p + 12 + kb.length;
        long alignedPayloadOffset = align(payloadOffset, 8);
        int internalPadding = (int) (alignedPayloadOffset - payloadOffset);

        int rawSize = 12 + kb.length + internalPadding + 4;

        m.set(INT, p + 4, ValueType.INTEGER.id);
        m.set(INT, p + 8, kb.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 12, kb.length);

        m.set(INT, alignedPayloadOffset, value);

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + maxEntrySize, Math::max);
        return offset;
    }

    long writeFloatArray(String key, float[] values) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        int floatsSize = values.length * 4;
        int rawPayloadHeaderSize = 16 + kb.length;
        int maxEntrySize = (int) align(rawPayloadHeaderSize + 7 + floatsSize, 8);

        long offset = reserved.getAndAdd(maxEntrySize);
        long p = offset % segmentSize;
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();

        long payloadOffset = p + 16 + kb.length;
        long alignedPayloadOffset = align(payloadOffset, 8);
        int internalPadding = (int) (alignedPayloadOffset - payloadOffset);

        int rawSize = 16 + kb.length + internalPadding + floatsSize;

        m.set(INT, p + 4, ValueType.FLOAT_ARRAY.id);
        m.set(INT, p + 8, kb.length);
        m.set(INT, p + 12, values.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 16, kb.length);

        MemorySegment.copy(MemorySegment.ofArray(values), 0, m, alignedPayloadOffset, (long) values.length * 4);

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + maxEntrySize, Math::max);
        return offset;
    }

    /**
     * Writes a matrix (nxn array) to the log. Expects the matrix to be squared (all columns have equal size)
     *
     * @param key    the key of this entry
     * @param values the values
     * @return the offset where the data was stored
     */
    long writeFloatMatrix(String key, float[][] values) {

        validateMatrix(values);

        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        int rowCount = values.length;
        int colCount = rowCount > 0 ? values[0].length : 0;
        int floatsSize = rowCount * colCount * 4;
        int rawPayloadHeaderSize = 20 + kb.length;
        int maxEntrySize = (int) align(rawPayloadHeaderSize + 7 + floatsSize, 8);

        long offset = reserved.getAndAdd(maxEntrySize);
        long p = offset % segmentSize;
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();

        long payloadOffset = p + 20 + kb.length;
        long alignedPayloadOffset = align(payloadOffset, 8);
        int internalPadding = (int) (alignedPayloadOffset - payloadOffset);

        int rawSize = 20 + kb.length + internalPadding + floatsSize;

        m.set(INT, p + 4, ValueType.FLOAT_MATRIX.id);
        m.set(INT, p + 8, kb.length);
        m.set(INT, p + 12, rowCount);
        m.set(INT, p + 16, colCount);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 20, kb.length);

        for (int i = 0; i < rowCount; i++) {
            MemorySegment.copy(MemorySegment.ofArray(values[i]), 0, m, alignedPayloadOffset + (long) i * colCount * 4, (long) colCount * 4);
        }

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + maxEntrySize, Math::max);
        return offset;
    }

    /**
     * Validates that the all the columns have the same length.
     *
     * @param values the matrix
     */
    private void validateMatrix(float[][] values) {
        if (values.length == 0) {
            return;
        }
        var length = values[0].length;
        for (int i = 1; i < values.length; i++) {
            if (values[i].length != length) {
                throw new IllegalArgumentException(
                        "Invalid matrix: first row is %d elements, but fieldName %d has %d"
                                .formatted(length, i, values[i].length));
            }
        }
    }

    int readInteger(long offset) {
        MemorySegment m = segmentFor(offset).memory();
        long p = offset % segmentSize;

        int len = m.get(INT, p);
        if (len <= 0) throw new IllegalStateException();

        ValueType t = ValueType.fromId(m.get(INT, p + 4));
        if (t != ValueType.INTEGER)
            throw new ClassCastException();

        int klen = m.get(INT, p + 8);
        long payloadOffset = p + 12 + klen;
        long alignedOffset = align(payloadOffset, 8);
        return m.get(INT, alignedOffset);
    }

    float[][] readFloatMatrix(long offset) {
        MemorySegment m = segmentFor(offset).memory();
        long p = offset % segmentSize;
        /*
        p    : [int entryLen]          // commit marker
        p + 4: [int dataType]
        p + 8: [int keyLen]
        p +12: [int rowCount]          // required to reconstruct the array
        p +16: [int colCount]          // required to reconstruct the array
        p +20: [key bytes]
        [padding]
        [data bytes]           // rowCount * colCount * 4
         */
        int len = m.get(INT, p);
        if (len <= 0) throw new IllegalStateException();

        ValueType t = ValueType.fromId(m.get(INT, p + 4));
        if (t != ValueType.FLOAT_MATRIX)
            throw new ClassCastException();

        int klen = m.get(INT, p + 8);
        int rowCount = m.get(INT, p + 12);
        int colCount = m.get(INT, p + 16);
        float[][] out = new float[rowCount][colCount];
        if (rowCount == 0) {
            return out;
        }

        long payloadOffset = p + 20 + klen;
        long alignedFloatsOffset = align(payloadOffset, 8);
        for (int i = 0; i < rowCount; i++) {
            MemorySegment.copy(m, alignedFloatsOffset + (long) i * colCount * 4, MemorySegment.ofArray(out[i]), 0, (long) colCount * 4);
        }
        return out;
    }

    float[] readFloatArray(long offset) {
        MemorySegment m = segmentFor(offset).memory();
        long p = offset % segmentSize;
        /*
        p    : [int entryLen]          // commit marker
        p + 4: [int dataType]
        p + 8: [int keyLen]
        p +12: [int floatCount]        // required to reconstruct the array
        p +16: [key bytes]
        [padding]
        [data bytes]           // floatCount * 4
         */
        int len = m.get(INT, p);
        if (len <= 0) throw new IllegalStateException();

        ValueType t = ValueType.fromId(m.get(INT, p + 4));
        if (t != ValueType.FLOAT_ARRAY)
            throw new ClassCastException();

        int klen = m.get(INT, p + 8);
        int count = m.get(INT, p + 12);

        float[] out = new float[count];
        if (count == 0) {
            return out;
        }

        long payloadOffset = p + 16 + klen;
        long alignedFloatsOffset = align(payloadOffset, 8);
        MemorySegment.copy(m, alignedFloatsOffset, MemorySegment.ofArray(out), 0, (long) count * 4);
        return out;
    }

    String readString(long offset) {
        byte[] bytes = readBytes(offset, ValueType.STRING);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    byte[] readBytes(long offset) {
        return readBytes(offset, ValueType.BYTES);
    }

    String readKey(long offset) {
        MemorySegment m = segmentFor(offset).memory();
        long p = offset % segmentSize;
        int len = m.get(INT, p);
        if (len <= 0) throw new IllegalStateException();

        int klen = m.get(INT, p + 8);
        byte[] out = new byte[klen];
        MemorySegment.copy(m, p + 12, MemorySegment.ofArray(out), 0, klen);
        return new String(out, StandardCharsets.UTF_8);
    }

    private byte[] readBytes(long offset, ValueType expectedType) {
        MemorySegment m = segmentFor(offset).memory();
        long p = offset % segmentSize;
        /*
        [int entryLen]
        [int dataType]
        [int keyLen]
        [key bytes]
        [padding]
        [data byes]
         */
        int len = m.get(INT, p);
        if (len <= 0) throw new IllegalStateException();

        ValueType t = ValueType.fromId(m.get(INT, p + 4));
        if (t != expectedType)
            throw new ClassCastException("Expected " + expectedType + " but found " + t + " at offset " + offset);

        int klen = m.get(INT, p + 8);

        // Header size depends on type
        int headerSize = 12;
        if (expectedType == ValueType.FLOAT_ARRAY) headerSize = 16;
        if (expectedType == ValueType.FLOAT_MATRIX) headerSize = 20;

        long payloadOffset = p + headerSize + klen;
        long alignedPayloadOffset = align(payloadOffset, 8);

        // vlen is accurately calculated from the stored len minus the distance to the aligned payload
        int vlen = len - (int) (alignedPayloadOffset - p);

        byte[] out = new byte[vlen];
        MemorySegment.copy(m, alignedPayloadOffset, MemorySegment.ofArray(out), 0, vlen);
        return out;
    }


    private Segment segmentFor(long globalOffset) {
        long segmentIndex = globalOffset / segmentSize;

        // Fast path: already created
        if (segmentIndex < segments.size()) {
            return segments.get((int) segmentIndex);
        }

        // Slow path: need to create missing segments
        segmentLock.lock();
        try {
            while (segmentIndex >= segments.size()) {
                long idx = segments.size();
                segments.add(createSegment(idx));
            }
            return segments.get((int) segmentIndex);
        } finally {
            segmentLock.unlock();
        }
    }

    private Segment createSegment(long index) {
        try {
            Path path = basePath.resolve("segment-" + index + ".dat");

            FileChannel ch = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );

            ch.truncate(segmentSize);

            Arena arena = Arena.ofShared();
            MemorySegment segment =
                    ch.map(FileChannel.MapMode.READ_WRITE, 0, segmentSize, arena);

            return new Segment(ch, arena, segment);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long committedOffset() {
        return committed.get();
    }

    public long offHeapBytes() {
        return committed.get();
    }


    private static long align(long v, long a) {
        return (v + a - 1) & ~(a - 1);
    }

    @Override
    public void close() throws Exception {
        for (Segment segment : segments) {
            segment.close();
        }
        segments.clear();
    }
}
