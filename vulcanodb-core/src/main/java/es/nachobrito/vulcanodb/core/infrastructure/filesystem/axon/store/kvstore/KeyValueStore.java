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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Key-Value Store
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

        long[] recoveredIndexOffsets = recoverIndexOffsets(
                baseDir.resolve("index"),
                bucketCount,
                committedIndexOffset
        );

        index = new HashIndex(
                baseDir.resolve("index"),
                bucketCount,
                segmentSize,
                recoveredIndexOffsets,
                dataLog
        );
    }

    /**
     * Recovers the persisted state.
     *
     * @param indexDir                   the path where the files are stored
     * @param bucketCount                the number of buckets
     * @param globalCommittedIndexOffset the global offset
     * @return the offsets, as a long array
     * @throws IOException if the data cannot be read
     */
    private static long[] recoverIndexOffsets(
            Path indexDir,
            int bucketCount,
            long globalCommittedIndexOffset
    ) throws IOException {

        long[] committed = new long[bucketCount];

        if (!Files.exists(indexDir)) {
            return committed; // all zeros
        }

        for (int bucket = 0; bucket < bucketCount; bucket++) {

            long offset = 0;
            long remaining = globalCommittedIndexOffset;

            int segmentIndex = 0;

            while (remaining > 0) {
                Path segPath = indexDir.resolve(
                        "index-b" + bucket + "-seg" + segmentIndex + ".idx");

                if (!Files.exists(segPath)) {
                    break;
                }

                try (FileChannel ch = FileChannel.open(
                        segPath,
                        StandardOpenOption.READ)) {

                    long segSize = ch.size();
                    long scanLimit = Math.min(segSize, remaining);

                    Arena arena = Arena.ofConfined();
                    MemorySegment seg =
                            ch.map(FileChannel.MapMode.READ_ONLY, 0, segSize, arena);

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
                }

                segmentIndex++;
            }

            committed[bucket] = offset;
        }

        return committed;
    }

    /**
     * Saves a string associated to the given key
     *
     * @param key   the key
     * @param value the value
     */
    public void putString(String key, String value) {

        // 1. Append to data log (returns global offset)
        long dataOffset = dataLog.writeString(key, value);

        // 2. Append to index AFTER data is committed
        index.put(key, dataOffset);

        // 3. Persist committed offsets (crash boundary)
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
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
     * Saves an int associated to the given key
     *
     * @param key   the key
     * @param value the value
     */
    public void putInt(String key, int value) {
        long dataOffset = dataLog.writeInteger(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
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
     * Saves a float array associated to the given key
     *
     * @param key   the key
     * @param value the value
     */
    public void putFloatArray(String key, float[] value) {
        long dataOffset = dataLog.writeFloatArray(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
    }

    /**
     * Saves a float matrix associated to the given key
     *
     * @param key   the key
     * @param value the value
     */
    public void putFloatMatrix(String key, float[][] value) {
        long dataOffset = dataLog.writeFloatMatrix(key, value);
        index.put(key, dataOffset);
        metadata.commit(
                dataLog.committedOffset(),
                index.committedOffset()
        );
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
}
