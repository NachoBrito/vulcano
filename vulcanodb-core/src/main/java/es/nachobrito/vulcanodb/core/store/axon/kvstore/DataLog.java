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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
 * [data byes]
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

        /*
        [int entryLen]
        [int dataType]
        [int keyLen]
        [key bytes]
        [padding]
        [data byes]
         */
        int rawSize = 12 + kb.length + vb.length;
        long size = align(rawSize, 8);

        long offset = reserved.getAndAdd(size);
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();
        long p = offset % segmentSize;

        m.set(INT, p + 4, type.id);
        m.set(INT, p + 8, kb.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 12, kb.length);

        long payloadOffset = p + 12 + kb.length;
        long alignedPayloadOffset = align(payloadOffset, 8);
        MemorySegment.copy(MemorySegment.ofArray(vb), 0, m, alignedPayloadOffset, vb.length);

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + size, Math::max);
        return offset;
    }

    long writeInteger(String key, int value) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        int rawSize = 12 + kb.length + 4;
        long size = align(rawSize, 8);

        long offset = reserved.getAndAdd(size);
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();
        long p = offset % segmentSize;

        m.set(INT, p + 4, ValueType.INTEGER.id);
        m.set(INT, p + 8, kb.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 12, kb.length);

        long alignedDataOffset = align(p + 12 + kb.length, 8);
        m.set(INT, alignedDataOffset, value);

        VarHandle.releaseFence();
        m.set(INT, p, (int) size);

        committed.accumulateAndGet(offset + size, Math::max);
        return offset;
    }

    long writeFloatArray(String key, float[] values) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);

        int floatsSize = values.length * 4;
        int rawSize = 12 + kb.length + 4 + floatsSize;
        long size = align(rawSize, 8);

        long offset = reserved.getAndAdd(size);
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();
        long p = offset % segmentSize;
        /*
        [int entryLen]          // commit marker
        [int dataType]
        [int keyLen]
        [int floatCount]        // required to reconstruct the array
        [key bytes]
        [padding]
        [data bytes]           // floatCount * 4
         */
        m.set(INT, p + 4, ValueType.FLOAT_ARRAY.id);
        m.set(INT, p + 8, kb.length);
        m.set(INT, p + 12, values.length);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 16, kb.length);

        long dataAlignedOffset = align(p + 16 + kb.length, 8);
        MemorySegment.copy(MemorySegment.ofArray(values), 0, m, dataAlignedOffset, (long) values.length * 4);

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + size, Math::max);
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

        int rawSize = 20 + kb.length + floatsSize;
        long size = align(rawSize, 8);

        long offset = reserved.getAndAdd(size);
        Segment s = segmentFor(offset);
        MemorySegment m = s.memory();
        long p = offset % segmentSize;
        /*
        [int entryLen]          // commit marker
        [int dataType]
        [int keyLen]
        [int rowCount]        // required to reconstruct the array
        [int colCount]        // required to reconstruct the array
        [key bytes]
        [padding]
        [data bytes]           // floatCount * 4
         */
        m.set(INT, p + 4, ValueType.FLOAT_MATRIX.id);
        m.set(INT, p + 8, kb.length);
        m.set(INT, p + 12, rowCount);
        m.set(INT, p + 16, colCount);
        MemorySegment.copy(MemorySegment.ofArray(kb), 0, m, p + 20, kb.length);

        long dataAlignedOffset = align(p + 20 + kb.length, 8);
        for (int i = 0; i < rowCount; i++) {
            MemorySegment.copy(MemorySegment.ofArray(values[i]), 0, m, dataAlignedOffset + (long) i * colCount * 4, (long) colCount * 4);
        }

        VarHandle.releaseFence();
        m.set(INT, p, rawSize);

        committed.accumulateAndGet(offset + size, Math::max);
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
        long alignedOffset = align(p + 12 + klen, 8);
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

        long alignedFloatsOffset = align(p + 20 + klen, 8);
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

        long alignedFloatsOffset = align(p + 16 + klen, 8);
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

        long payloadOffset = p + 12 + klen;
        long alignedPayloadOffset = align(payloadOffset, 8);

        // We still need the length of the payload. 
        // In writeBytes, rawSize = 12 + kb.length + vb.length
        // So vb.length = rawSize - 12 - kb.length
        int vlen = len - 12 - klen;

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
        synchronized (segments) {
            while (segmentIndex >= segments.size()) {
                long idx = segments.size();
                segments.add(createSegment(idx));
            }
            return segments.get((int) segmentIndex);
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
