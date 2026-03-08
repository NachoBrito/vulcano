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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana;

import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.TucanaStorage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of {@link KeyValueStore} based on the Tucana architecture.
 */
public class TucanaKeyValueStore implements KeyValueStore {

    private final TucanaIndex index;
    private final TucanaStorage storage;

    /**
     * Constructs a new TucanaKeyValueStore.
     *
     * @param index   the index to use for key lookups
     * @param storage the storage to use for persisting data
     */
    public TucanaKeyValueStore(TucanaIndex index, TucanaStorage storage) {
        this.index = index;
        this.storage = storage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putString(String key, String value) {
        return putString(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putString(String key, String value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getString(String key) {
        var offsetOpt = index.get(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readStringValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putInt(String key, int value) {
        return putInt(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putInt(String key, int value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Integer> getInt(String key) {
        var offsetOpt = index.get(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readIntValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putFloatArray(String key, float[] value) {
        return putFloatArray(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return putFloatMatrix(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<float[]> getFloatArray(String key) {
        var offsetOpt = index.get(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readFloatArrayValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        var offsetOpt = index.get(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readFloatMatrixValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putBytes(String key, byte[] value) {
        return putBytes(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    private long persist(String key, ByteBuffer entry, boolean commit) {
        long offset = storage.write(entry);
        index.upsert(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)), offset);
        if (commit) {
            commit();
        }
        return offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        storage.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<byte[]> getBytes(String key) {
        var offsetOpt = index.get(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readByteArrayValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(String key) {
        index.delete(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long offHeapBytes() {
        return 0; // Simplified for now
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<Long> getOffsetStream() {
        return index.allOffsets();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringAt(long offset) {
        return Entry.readStringValue(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKeyAt(long offset) {
        return Entry.readKey(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntAt(long offset) {
        return Entry.readIntValue(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] getFloatArrayAt(long offset) {
        return Entry.readFloatArrayValue(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[][] getFloatMatrixAt(long offset) {
        return Entry.readFloatMatrixValue(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getBytesAt(long offset) {
        return Entry.readByteArrayValue(storage.read(offset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        storage.close();
    }
}
