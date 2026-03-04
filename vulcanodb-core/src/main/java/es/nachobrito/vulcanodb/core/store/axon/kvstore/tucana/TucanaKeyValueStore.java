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

    public TucanaKeyValueStore(TucanaIndex index, TucanaStorage storage) {
        this.index = index;
        this.storage = storage;
    }

    @Override
    public long putString(String key, String value) {
        return putString(key, value, false);
    }

    @Override
    public long putString(String key, String value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    @Override
    public Optional<String> getString(String key) {
        var offsetOpt = index.get(key.getBytes(StandardCharsets.UTF_8));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readStringValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    @Override
    public long putInt(String key, int value) {
        return putInt(key, value, false);
    }

    @Override
    public long putInt(String key, int value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    @Override
    public Optional<Integer> getInt(String key) {
        var offsetOpt = index.get(key.getBytes(StandardCharsets.UTF_8));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readIntValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    @Override
    public long putFloatArray(String key, float[] value) {
        return putFloatArray(key, value, false);
    }

    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return putFloatMatrix(key, value, false);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    @Override
    public Optional<float[]> getFloatArray(String key) {
        var offsetOpt = index.get(key.getBytes(StandardCharsets.UTF_8));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readFloatArrayValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        var offsetOpt = index.get(key.getBytes(StandardCharsets.UTF_8));
        if (offsetOpt.isPresent()) {
            return Optional.of(Entry.readFloatMatrixValue(storage.read(offsetOpt.getAsLong())));
        }
        return Optional.empty();
    }

    @Override
    public long putBytes(String key, byte[] value) {
        return putBytes(key, value, false);
    }

    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        return persist(key, Entry.of(key, value), commit);
    }

    private long persist(String key, ByteBuffer entry, boolean commit) {
        byte[] bytes = entry.array();
        long offset = storage.write(bytes);
        index.upsert(key.getBytes(StandardCharsets.UTF_8), offset);
        if (commit) {
            commit();
        }
        return index.rootOffset();
    }

    @Override
    public void commit() {
        storage.commit();
    }

    @Override
    public Optional<byte[]> getBytes(String key) {
        var offsetOpt = index.get(key.getBytes(StandardCharsets.UTF_8));
        if (offsetOpt.isPresent()) {
            var buffer = storage.read(offsetOpt.getAsLong());
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return Optional.of(bytes);
        }
        return Optional.empty();
    }

    @Override
    public void remove(String key) {
        index.delete(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public long offHeapBytes() {
        return 0; // Simplified for now
    }

    @Override
    public Stream<Long> getOffsetStream() {
        return index.allOffsets();
    }

    @Override
    public String getStringAt(long offset) {
        return Entry.readStringValue(storage.read(offset));
    }

    @Override
    public String getKeyAt(long offset) {
        return Entry.readKey(storage.read(offset));
    }

    @Override
    public int getIntAt(long offset) {
        return Entry.readIntValue(storage.read(offset));
    }

    @Override
    public float[] getFloatArrayAt(long offset) {
        return Entry.readFloatArrayValue(storage.read(offset));
    }

    @Override
    public float[][] getFloatMatrixAt(long offset) {
        return Entry.readFloatMatrixValue(storage.read(offset));
    }

    @Override
    public byte[] getBytesAt(long offset) {
        var buffer = storage.read(offset);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public void close() throws Exception {
        storage.close();
    }
}
