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

import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Java 25 implementation of Tucana Key-Value Store.
 * Uses Bε-trees, Copy-on-Write persistence, and multi-segment storage.
 *
 * @author nacho
 */
public final class TucanaKeyValueStore implements KeyValueStore {

    private final StorageManager storageManager;
    private final DataLog dataLog;
    private final BεTree index;

    private final ReentrantLock writeLock = new ReentrantLock();

    /**
     * Map of historical epoch roots for versioned queries.
     */
    private final java.util.Map<Long, Long> epochHistory = new java.util.concurrent.ConcurrentHashMap<>();

    public TucanaKeyValueStore(Path baseDir) throws IOException {
        this(baseDir, 64L * 1024 * 1024); // Default 64 MB segments
    }

    public TucanaKeyValueStore(Path baseDir, long segmentSize) throws IOException {
        this.storageManager = new StorageManager(baseDir, segmentSize);

        // Recovery Logic (Superblock is always in Segment 0, Page 0)
        MemorySegment superblock = storageManager.getSegment(0).getPage(0);
        long magic = (long) Layout.SB_MAGIC.get(superblock, 0L);
        long rootOffset = 0;
        long logTail = 0;

        if (magic == 0x545543414E41L) {
            if (verifySuperblockChecksum(superblock)) {
                rootOffset = (long) Layout.SB_ROOT.get(superblock, 0L);
                logTail = (long) Layout.SB_LOG_TAIL.get(superblock, 0L);
                loadEpochHistory(superblock);
            } else {
                throw new IOException("Tucana superblock checksum mismatch. Data might be corrupted.");
            }
        }

        this.dataLog = new DataLog(storageManager, logTail);
        this.index = new BεTree(storageManager, dataLog, rootOffset);
    }

    private void loadEpochHistory(MemorySegment superblock) {
        long historyOffset = (long) Layout.SB_HISTORY.get(superblock, 0L);
        if (historyOffset == 0) return;

        // Simplified history loading: read from history log page
        MemorySegment historyPage = storageManager.getSegmentForOffset(historyOffset).getPage(storageManager.localOffset(historyOffset));
        int numEntries = (int) (Layout.PAGE_SIZE / Layout.HISTORY_ENTRY.byteSize());
        for (int i = 0; i < numEntries; i++) {
            long offset = (long) i * Layout.HISTORY_ENTRY.byteSize();
            long epoch = historyPage.get(ValueLayout.JAVA_LONG, offset);
            long root = historyPage.get(ValueLayout.JAVA_LONG, offset + 8);
            if (root != 0) {
                epochHistory.put(epoch, root);
            }
        }
    }

    private boolean verifySuperblockChecksum(MemorySegment superblock) {
        long storedChecksum = (long) Layout.SB_CHECKSUM.get(superblock, 0L);
        return storedChecksum == calculateSuperblockChecksum(superblock);
    }

    private long calculateSuperblockChecksum(MemorySegment superblock) {
        CRC32 crc = new CRC32();
        // Checksum everything except the checksum field itself
        byte[] data = superblock.asSlice(0, Layout.SUPERBLOCK.byteSize() - 8).toArray(ValueLayout.JAVA_BYTE);
        crc.update(data);
        return crc.getValue();
    }

    @Override
    public long putString(String key, String value) {
        return putString(key, value, true);
    }

    @Override
    public long putString(String key, String value, boolean commit) {
        writeLock.lock();
        try {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            long dataOffset = dataLog.write(key, bytes, (byte) 1);
            index.insert(key, dataOffset);

            if (commit) {
                commit();
            }
            return dataOffset;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<String> getString(String key) {
        return getStringAtEpoch(key, storageManager.currentEpoch() - 1);
    }

    /**
     * Retrieves the String associated to the given key as it existed at the end of the specified epoch.
     */
    public Optional<String> getStringAtEpoch(String key, long epoch) {
        long root = (epoch == storageManager.currentEpoch() - 1) 
                ? index.getRootOffset() 
                : epochHistory.getOrDefault(epoch, -1L);

        if (root == -1L) {
            return Optional.empty();
        }

        long offset = index.searchAtRoot(key, root);
        if (offset == -1) {
            return Optional.empty();
        }
        return Optional.of(new String(dataLog.readValue(offset), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public long putInt(String key, int value) {
        return putInt(key, value, true);
    }

    @Override
    public long putInt(String key, int value, boolean commit) {
        writeLock.lock();
        try {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4);
            bb.putInt(value);
            long dataOffset = dataLog.write(key, bb.array(), (byte) 2);
            index.insert(key, dataOffset);
            if (commit) commit();
            return dataOffset;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<Integer> getInt(String key) {
        long offset = index.search(key);
        if (offset == -1) return Optional.empty();
        return Optional.of(java.nio.ByteBuffer.wrap(dataLog.readValue(offset)).getInt());
    }

    @Override
    public long putFloatArray(String key, float[] value) {
        return putFloatArray(key, value, true);
    }

    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        writeLock.lock();
        try {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(value.length * 4);
            for (float f : value) bb.putFloat(f);
            long dataOffset = dataLog.write(key, bb.array(), (byte) 3);
            index.insert(key, dataOffset);
            if (commit) commit();
            return dataOffset;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return 0;
    }

    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        return 0;
    }

    @Override
    public Optional<float[]> getFloatArray(String key) {
        long offset = index.search(key);
        if (offset == -1) return Optional.empty();
        byte[] bytes = dataLog.readValue(offset);
        float[] result = new float[bytes.length / 4];
        java.nio.ByteBuffer.wrap(bytes).asFloatBuffer().get(result);
        return Optional.of(result);
    }

    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        return Optional.empty();
    }

    @Override
    public long putBytes(String key, byte[] value) {
        return putBytes(key, value, true);
    }

    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        writeLock.lock();
        try {
            long dataOffset = dataLog.write(key, value, (byte) 0);
            index.insert(key, dataOffset);
            if (commit) commit();
            return dataOffset;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void commit() {
        writeLock.lock();
        try {
            MemorySegment superblock = storageManager.getSegment(0).getPage(0);
            long newEpoch = storageManager.currentEpoch();
            long rootOffset = index.getRootOffset();

            // Store in history
            epochHistory.put(newEpoch, rootOffset);
            updatePersistentHistory(newEpoch, rootOffset);

            Layout.SB_MAGIC.set(superblock, 0L, 0x545543414E41L);
            Layout.SB_EPOCH.set(superblock, 0L, newEpoch);
            Layout.SB_ROOT.set(superblock, 0L, rootOffset);
            Layout.SB_LOG_TAIL.set(superblock, 0L, dataLog.getTailOffset());

            long checksum = calculateSuperblockChecksum(superblock);
            Layout.SB_CHECKSUM.set(superblock, 0L, checksum);

            storageManager.fsyncAll();
            storageManager.processFreeLog(newEpoch);
            storageManager.incrementEpoch();
        } finally {
            writeLock.unlock();
        }
    }

    private void updatePersistentHistory(long epoch, long rootOffset) throws IOException {
        // Simple persistent history: use page 1 of segment 0 for now
        MemorySegment superblock = storageManager.getSegment(0).getPage(0);
        long historyOffset = (long) Layout.SB_HISTORY.get(superblock, 0L);
        if (historyOffset == 0) {
            historyOffset = storageManager.allocatePage();
            Layout.SB_HISTORY.set(superblock, 0L, historyOffset);
        }

        MemorySegment historyPage = storageManager.getSegmentForOffset(historyOffset).getPage(storageManager.localOffset(historyOffset));
        // Find slot for this epoch
        long slot = (epoch % (Layout.PAGE_SIZE / Layout.HISTORY_ENTRY.byteSize())) * Layout.HISTORY_ENTRY.byteSize();
        historyPage.set(ValueLayout.JAVA_LONG, slot, epoch);
        historyPage.set(ValueLayout.JAVA_LONG, slot + 8, rootOffset);
    }

    @Override
    public Optional<byte[]> getBytes(String key) {
        long offset = index.search(key);
        if (offset == -1) return Optional.empty();
        return Optional.of(dataLog.readValue(offset));
    }

    @Override
    public void remove(String key) {
        writeLock.lock();
        try {
            index.insert(key, -1);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long offHeapBytes() {
        return storageManager.totalSize();
    }

    @Override
    public Stream<Long> getOffsetStream() {
        return Stream.empty();
    }

    @Override
    public String getStringAt(long offset) {
        return new String(dataLog.readValue(offset), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public String getKeyAt(long offset) {
        return dataLog.readKey(offset);
    }

    @Override
    public int getIntAt(long offset) {
        return java.nio.ByteBuffer.wrap(dataLog.readValue(offset)).getInt();
    }

    @Override
    public float[] getFloatArrayAt(long offset) {
        byte[] bytes = dataLog.readValue(offset);
        float[] result = new float[bytes.length / 4];
        java.nio.ByteBuffer.wrap(bytes).asFloatBuffer().get(result);
        return result;
    }

    @Override
    public float[][] getFloatMatrixAt(long offset) {
        return new float[0][0];
    }

    @Override
    public byte[] getBytesAt(long offset) {
        return dataLog.readValue(offset);
    }

    @Override
    public void close() throws Exception {
        commit();
        storageManager.close();
    }
}
