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
        index.upsert(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
        if (commit) {
            commit();
        }
        return index.rootOffset();
    }

    @Override
    public Optional<String> getString(String key) {
        return index.get(key.getBytes(StandardCharsets.UTF_8)).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public long putInt(String key, int value) {
        return putInt(key, value, false);
    }

    @Override
    public long putInt(String key, int value, boolean commit) {
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).putInt(value);
        index.upsert(key.getBytes(StandardCharsets.UTF_8), bytes);
        if (commit) {
            commit();
        }
        return index.rootOffset();
    }

    @Override
    public Optional<Integer> getInt(String key) {
        return index.get(key.getBytes(StandardCharsets.UTF_8)).map(bytes -> ByteBuffer.wrap(bytes).getInt());
    }

    @Override
    public long putFloatArray(String key, float[] value) {
        return putFloatArray(key, value, false);
    }

    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        byte[] bytes = new byte[value.length * 4];
        ByteBuffer.wrap(bytes).asFloatBuffer().put(value);
        index.upsert(key.getBytes(StandardCharsets.UTF_8), bytes);
        if (commit) {
            commit();
        }
        return index.rootOffset();
    }

    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return putFloatMatrix(key, value, false);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        int rows = value.length;
        int cols = rows > 0 ? value[0].length : 0;
        byte[] bytes = new byte[8 + rows * cols * 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(rows);
        buffer.putInt(cols);
        for (float[] row : value) {
            buffer.asFloatBuffer().put(row);
            buffer.position(buffer.position() + row.length * 4);
        }
        index.upsert(key.getBytes(StandardCharsets.UTF_8), bytes);
        if (commit) {
            commit();
        }
        return index.rootOffset();
    }

    @Override
    public Optional<float[]> getFloatArray(String key) {
        return index.get(key.getBytes(StandardCharsets.UTF_8)).map(bytes -> {
            float[] floats = new float[bytes.length / 4];
            ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
            return floats;
        });
    }

    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        return index.get(key.getBytes(StandardCharsets.UTF_8)).map(bytes -> {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int rows = buffer.getInt();
            int cols = buffer.getInt();
            float[][] matrix = new float[rows][cols];
            for (int i = 0; i < rows; i++) {
                buffer.asFloatBuffer().get(matrix[i]);
                buffer.position(buffer.position() + cols * 4);
            }
            return matrix;
        });
    }

    @Override
    public long putBytes(String key, byte[] value) {
        return putBytes(key, value, false);
    }

    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        index.upsert(key.getBytes(StandardCharsets.UTF_8), value);
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
        return index.get(key.getBytes(StandardCharsets.UTF_8));
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
        return new String(storage.read(offset), StandardCharsets.UTF_8);
    }

    @Override
    public String getKeyAt(long offset) {
        // In Tucana, the stored record at an offset might be just the value, 
        // or it might include the key. For now, we assume it's recoverable or 
        // we'll refine this once the storage layout is finalized.
        // Assuming for now it's just the value or handled similarly to getStringAt
        return getStringAt(offset);
    }

    @Override
    public int getIntAt(long offset) {
        return ByteBuffer.wrap(storage.read(offset)).getInt();
    }

    @Override
    public float[] getFloatArrayAt(long offset) {
        byte[] bytes = storage.read(offset);
        float[] floats = new float[bytes.length / 4];
        ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
        return floats;
    }

    @Override
    public float[][] getFloatMatrixAt(long offset) {
        ByteBuffer buffer = ByteBuffer.wrap(storage.read(offset));
        int rows = buffer.getInt();
        int cols = buffer.getInt();
        float[][] matrix = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            buffer.asFloatBuffer().get(matrix[i]);
            buffer.position(buffer.position() + cols * 4);
        }
        return matrix;
    }

    @Override
    public byte[] getBytesAt(long offset) {
        return storage.read(offset);
    }

    @Override
    public void close() throws Exception {
        storage.close();
    }
}
