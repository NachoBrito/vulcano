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

package es.nachobrito.vulcanodb.core.store.axon.wal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance append-only log for WAL entries using Foreign Function & Memory API.
 * Each entry: [int len] [int status] [long txId] [payload bytes]
 * status: 0 = uncommitted, 1 = committed
 */
final class WalLog implements AutoCloseable {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;

    private static final long SEGMENT_SIZE = 64L * 1024 * 1024; // 64 MB
    private static final int STATUS_UNCOMMITTED = 0;
    private static final int STATUS_COMMITTED = 1;

    private final Path basePath;
    private final List<LogSegment> segments = new ArrayList<>();
    private final AtomicLong writeOffset = new AtomicLong(0);
    private final ReentrantLock segmentLock = new ReentrantLock();

    WalLog(Path basePath) throws IOException {
        this.basePath = basePath;
        Files.createDirectories(basePath);
        initialize();
    }

    private void initialize() throws IOException {
        int i = 0;
        while (true) {
            Path path = segmentPath(i);
            if (path.toFile().exists()) {
                segments.add(openSegment(i));
                i++;
            } else {
                break;
            }
        }
        if (segments.isEmpty()) {
            segments.add(openSegment(0));
        }

        // Find actual end of log by scanning entries
        long offset = 0;
        for (LogSegment s : segments) {
            long segmentOffset = 0;
            while (segmentOffset + 4 <= SEGMENT_SIZE) {
                int len = s.memory().get(INT, segmentOffset);
                if (len <= 0) break;
                segmentOffset += align(len, 8);
            }
            if (segmentOffset < SEGMENT_SIZE && s.memory().get(INT, segmentOffset) == 0) {
                offset = (long) segments.indexOf(s) * SEGMENT_SIZE + segmentOffset;
                break;
            }
            offset = (long) (segments.indexOf(s) + 1) * SEGMENT_SIZE;
        }
        writeOffset.set(offset);
    }

    long append(long txId, byte[] payload) {
        int len = 4 + 4 + 8 + payload.length; // len + status + txId + payload
        long size = align(len, 8);

        long offset;
        long p;
        LogSegment s;

        segmentLock.lock();
        try {
            offset = writeOffset.get();
            p = offset % SEGMENT_SIZE;
            if (p + size > SEGMENT_SIZE) {
                // Doesn't fit in current segment, move to next one
                offset = (offset / SEGMENT_SIZE + 1) * SEGMENT_SIZE;
                p = 0;
            }
            writeOffset.set(offset + size);
            s = segmentFor(offset);
        } finally {
            segmentLock.unlock();
        }

        MemorySegment m = s.memory();

        m.set(INT, p + 4, STATUS_UNCOMMITTED);
        m.set(LONG, p + 8, txId);
        MemorySegment.copy(MemorySegment.ofArray(payload), 0, m, p + 16, payload.length);

        VarHandle.releaseFence();
        m.set(INT, p, len);

        return offset;
    }

    // Improved markCommitted with a scan from start of segments (WALs are usually small)
    void markCommitted(long txId) {
        for (LogSegment s : segments) {
            long segmentOffset = 0;
            while (segmentOffset + 16 <= SEGMENT_SIZE) {
                int len = s.memory().get(INT, segmentOffset);
                if (len <= 0) break;
                long entryTxId = s.memory().get(LONG, segmentOffset + 8);
                if (entryTxId == txId) {
                    s.memory().set(INT, segmentOffset + 4, STATUS_COMMITTED);
                    return;
                }
                segmentOffset += align(len, 8);
            }
        }
    }

    java.util.stream.Stream<byte[]> uncommittedStream() {
        return segments.stream().flatMap(s -> {
            List<byte[]> segmentEntries = new ArrayList<>();
            long segmentOffset = 0;
            while (segmentOffset + 16 <= SEGMENT_SIZE) {
                int len = s.memory().get(INT, segmentOffset);
                if (len <= 0) break;

                int status = s.memory().get(INT, segmentOffset + 4);
                if (status == STATUS_UNCOMMITTED) {
                    int payloadLen = len - 16;
                    byte[] payload = new byte[payloadLen];
                    MemorySegment.copy(s.memory(), segmentOffset + 16, MemorySegment.ofArray(payload), 0, payloadLen);
                    segmentEntries.add(payload);
                }
                segmentOffset += align(len, 8);
            }
            return segmentEntries.stream();
        });
    }

    private LogSegment segmentFor(long globalOffset) {
        int idx = (int) (globalOffset / SEGMENT_SIZE);
        segmentLock.lock();
        try {
            while (segments.size() <= idx) {
                segments.add(openSegment(segments.size()));
            }
            return segments.get(idx);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            segmentLock.unlock();
        }
    }

    private LogSegment openSegment(int index) throws IOException {
        Path path = segmentPath(index);
        FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (ch.size() < SEGMENT_SIZE) {
            ch.truncate(SEGMENT_SIZE);
        }
        Arena arena = Arena.ofShared();
        MemorySegment m = ch.map(FileChannel.MapMode.READ_WRITE, 0, SEGMENT_SIZE, arena);
        return new LogSegment(ch, arena, m);
    }

    private Path segmentPath(int index) {
        return basePath.resolve("wal-" + index + ".log");
    }

    private static long align(long v, long a) {
        return (v + a - 1) & ~(a - 1);
    }

    @Override
    public void close() throws Exception {
        for (LogSegment s : segments) {
            s.close();
        }
        segments.clear();
    }

    private record LogSegment(FileChannel channel, Arena arena, MemorySegment memory) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            arena.close();
            channel.close();
        }
    }
}
