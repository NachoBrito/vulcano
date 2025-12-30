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
public final class KeyValueStore implements AutoCloseable {

    private final Metadata metadata;
    private final DataLog dataLog;
    private final HashIndex index;

    /**
     * Creates a new KVStore that will use provided path for data storage.
     *
     * @param baseDir the data store folder
     * @throws IOException if the store setup fails.
     */
    public KeyValueStore(Path baseDir) throws IOException {
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


    /**
     * Saves a string associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getString(String)} or {@link #getStringAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    public long putString(String key, String value) {

        // 1. Append to data log (returns global offset)
        long dataOffset = dataLog.writeString(key, value);

        // 2. Append to index AFTER data is committed
        index.put(key, dataOffset);

        // 3. Persist committed offsets (crash boundary)
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );

        return dataOffset;
    }

    /**
     * Retrieves the String associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    public Optional<String> getString(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readString(offset));
    }

    /**
     * Saves an int associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getInt(String)} or {@link #getIntAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    public long putInt(String key, int value) {
        long dataOffset = dataLog.writeInteger(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
        return dataOffset;
    }

    /**
     * Retrieves the Integer associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    public Optional<Integer> getInt(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }

        return Optional.of(dataLog.readInteger(offset));
    }

    /**
     * Saves a float array associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getFloatArray(String)} or {@link #getFloatArrayAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    public long putFloatArray(String key, float[] value) {
        long dataOffset = dataLog.writeFloatArray(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
        return dataOffset;
    }

    /**
     * Saves a float matrix associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getFloatMatrix(String)} or {@link #getFloatMatrixAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    public long putFloatMatrix(String key, float[][] value) {
        long dataOffset = dataLog.writeFloatMatrix(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
        return dataOffset;
    }

    /**
     * Retrieves the float array associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    public Optional<float[]> getFloatArray(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readFloatArray(offset));
    }

    /**
     * Retrieves the float array associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    public Optional<float[][]> getFloatMatrix(String key) {
        long offset = index.get(key);
        if (offset < 0) {
            return Optional.empty();
        }
        return Optional.of(dataLog.readFloatMatrix(offset));
    }

    /**
     * Removes the value associated to the provided key
     *
     * @param key the key to remove.
     */
    public void remove(String key) {
        index.put(key, -1);
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

    /**
     * Returns a Stream that provides valid offsets where keys of this store can be found. Values can then be read by
     * passing these offsets to {@link #getStringAt(long)}, {@link #getIntAt(long)}, {@link #getFloatArrayAt(long)},
     * {@link #getFloatMatrixAt(long)}
     *
     * @return a Stream of valid Long offsets.
     */
    public Stream<Long> getOffsetStream() {
        return index.valueOffsets();
    }

    public String getStringAt(long offset) {
        return dataLog.readString(offset);
    }

    public int getIntAt(long offset) {
        return dataLog.readInteger(offset);
    }

    public float[] getFloatArrayAt(long offset) {
        return dataLog.readFloatArray(offset);
    }

    public float[][] getFloatMatrixAt(long offset) {
        return dataLog.readFloatMatrix(offset);
    }
}
