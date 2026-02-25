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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-segment append-only data log for Tucana.
 *
 * @author nacho
 */
public final class DataLog {

    private final StorageManager storageManager;
    private final AtomicLong tailOffset;

    public DataLog(StorageManager storageManager, long initialTail) {
        this.storageManager = storageManager;
        this.tailOffset = new AtomicLong(initialTail);

        if (this.tailOffset.get() == 0) {
            // Skip superblock in the first segment
            this.tailOffset.set(Layout.PAGE_SIZE);
        }
    }

    public long write(String key, byte[] value, byte valueType) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        int entrySize = (int) (Layout.LOG_ENTRY_HEADER.byteSize() + keyBytes.length + value.length);

        long globalOffset;
        synchronized (this) {
            globalOffset = tailOffset.get();
            // Check if entry fits in current segment
            long localOffset = storageManager.localOffset(globalOffset);
            if (localOffset + entrySize > storageManager.getSegmentForOffset(globalOffset).size()) {
                // Move to next segment
                globalOffset = (long) (storageManager.getSegmentForOffset(globalOffset).id() + 1) * storageManager.getSegmentForOffset(globalOffset).size();
                tailOffset.set(globalOffset);
            }
            tailOffset.addAndGet(entrySize);
        }

        SegmentManager segment = storageManager.getSegmentForOffset(globalOffset);
        long localOffset = storageManager.localOffset(globalOffset);
        MemorySegment entry = segment.getFullSegment().asSlice(localOffset, entrySize);

        entry.set(ValueLayout.JAVA_INT, 0, keyBytes.length);
        entry.set(ValueLayout.JAVA_INT, 4, value.length);
        entry.set(ValueLayout.JAVA_BYTE, 8, valueType);

        MemorySegment.copy(MemorySegment.ofArray(keyBytes), 0, entry, Layout.LOG_ENTRY_HEADER.byteSize(), keyBytes.length);
        MemorySegment.copy(MemorySegment.ofArray(value), 0, entry, Layout.LOG_ENTRY_HEADER.byteSize() + keyBytes.length, value.length);

        return globalOffset;
    }

    public byte[] readValue(long globalOffset) {
        SegmentManager segment = storageManager.getSegmentForOffset(globalOffset);
        long localOffset = storageManager.localOffset(globalOffset);
        MemorySegment fullSeg = segment.getFullSegment();

        int keySize = fullSeg.get(ValueLayout.JAVA_INT, localOffset);
        int valueSize = fullSeg.get(ValueLayout.JAVA_INT, localOffset + 4);

        long valLocalOffset = localOffset + Layout.LOG_ENTRY_HEADER.byteSize() + keySize;
        return fullSeg.asSlice(valLocalOffset, valueSize).toArray(ValueLayout.JAVA_BYTE);
    }

    public String readKey(long globalOffset) {
        SegmentManager segment = storageManager.getSegmentForOffset(globalOffset);
        long localOffset = storageManager.localOffset(globalOffset);
        MemorySegment fullSeg = segment.getFullSegment();

        int keySize = fullSeg.get(ValueLayout.JAVA_INT, localOffset);
        long keyLocalOffset = localOffset + Layout.LOG_ENTRY_HEADER.byteSize();
        
        byte[] keyBytes = fullSeg.asSlice(keyLocalOffset, keySize).toArray(ValueLayout.JAVA_BYTE);
        return new String(keyBytes, StandardCharsets.UTF_8);
    }

    public long getTailOffset() {
        return tailOffset.get();
    }
}
