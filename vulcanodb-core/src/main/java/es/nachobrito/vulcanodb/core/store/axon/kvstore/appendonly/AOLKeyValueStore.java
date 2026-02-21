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

import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Key-Value Store. Stores values of supported types (currently string, int, float[], float[][]) associated to
 * arbitrary string keys.
 * <p>
 * This class also provides access to internal Long ids (i.e. offsets) for rapid data access when the key is not needed.
 *
 * @author nacho
 */
public final class AOLKeyValueStore implements KeyValueStore {

    private final Metadata metadata;
    private final DataLog dataLog;
    private final HashIndex index;

    /**
     * Creates a new KVStore that will use provided path for data storage.
     *
     * @param baseDir the data store folder
     * @throws IOException if the store setup fails.
     */
    public AOLKeyValueStore(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);
        Files.createDirectories(baseDir.resolve("data", "segment"));
        Files.createDirectories(baseDir.resolve("index"));

        // ---- Metadata ----
        metadata = new Metadata(baseDir.resolve("metadata.dat"));

        long committedDataOffset = metadata.dataOffset();
        long committedIndexOffset = metadata.indexOffset();

        // ---- Data log ----
        dataLog = new DataLog(
                baseDir.resolve("data/segment"),
                256L * 1024 * 1024,      // 256 MB segments
                committedDataOffset
        );

        // ---- Hash index ----
        int bucketCount = 1 << 16;     // 65k buckets
        long segmentSize = 16L * 1024 * 1024;

        index = new HashIndex(
                baseDir.resolve("index"),
                bucketCount,
                segmentSize,
                committedIndexOffset,
                dataLog
        );
    }


    @Override
    public long putString(String key, String value) {
        return putString(key, value, true);
    }

    @Override
    public long putString(String key, String value, boolean commit) {

        // 1. Append to data log (returns global offset)
        long dataOffset = dataLog.writeString(key, value);

        // 2. Append to index AFTER data is committed
        index.put(key, dataOffset);

        // 3. Persist committed offsets (crash boundary)
        if (commit) {
            commit();
        }

        return dataOffset;
    }

    @Override
    public Optional<String> getString(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readString(offset));
    }

    @Override
    public long putInt(String key, int value) {
        return putInt(key, value, true);
    }

    @Override
    public long putInt(String key, int value, boolean commit) {
        long dataOffset = dataLog.writeInteger(key, value);
        index.put(key, dataOffset);
        if (commit) {
            commit();
        }
        return dataOffset;
    }

    @Override
    public Optional<Integer> getInt(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }

        return Optional.of(dataLog.readInteger(offset));
    }

    @Override
    public long putFloatArray(String key, float[] value) {
        return putFloatArray(key, value, true);
    }

    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        long dataOffset = dataLog.writeFloatArray(key, value);
        index.put(key, dataOffset);
        if (commit) {
            commit();
        }
        return dataOffset;
    }

    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return putFloatMatrix(key, value, true);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        long dataOffset = dataLog.writeFloatMatrix(key, value);
        index.put(key, dataOffset);
        if (commit) {
            commit();
        }
        return dataOffset;
    }

    @Override
    public Optional<float[]> getFloatArray(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readFloatArray(offset));
    }

    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readFloatMatrix(offset));
    }

    @Override
    public long putBytes(String key, byte[] value) {
        return putBytes(key, value, true);
    }

    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        long dataOffset = dataLog.writeBytes(key, value);
        index.put(key, dataOffset);
        if (commit) {
            commit();
        }
        return dataOffset;
    }

    @Override
    public void commit() {
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
    }

    @Override
    public Optional<byte[]> getBytes(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readBytes(offset));
    }

    @Override
    public void remove(String key) {
        index.put(key, -1);
    }

    @Override
    public long offHeapBytes() {
        return dataLog.offHeapBytes() + index.offHeapBytes();
    }

    @Override
    public void close() throws Exception {
        // 1. Capture durable boundaries
        long dataOffset = dataLog.committedOffset();
        long indexOffset = index.committedOffset();

        // 2. Persist metadata atomically
        metadata.commit(dataOffset, indexOffset);

        // 3. Force metadata to disk
        metadata.fsync();

        // 4. Close segments / arenas
        dataLog.close();
        index.close();
    }

    @Override
    public Stream<Long> getOffsetStream() {
        return index.valueOffsets();
    }

    @Override
    public String getStringAt(long offset) {
        return dataLog.readString(offset);
    }

    @Override
    public String getKeyAt(long offset) {
        return dataLog.readKey(offset);
    }

    @Override
    public int getIntAt(long offset) {
        return dataLog.readInteger(offset);
    }

    @Override
    public float[] getFloatArrayAt(long offset) {
        return dataLog.readFloatArray(offset);
    }

    @Override
    public float[][] getFloatMatrixAt(long offset) {
        return dataLog.readFloatMatrix(offset);
    }

    @Override
    public byte[] getBytesAt(long offset) {
        return dataLog.readBytes(offset);
    }
}
