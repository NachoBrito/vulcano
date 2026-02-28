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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TucanaKeyValueStore}.
 * These tests follow the TDD approach and are expected to fail initially.
 */
class TucanaKeyValueStoreTest {

    private TucanaIndex index;
    private TucanaStorage storage;
    private TucanaKeyValueStore kvStore;

    @BeforeEach
    void setUp() {
        index = mock(TucanaIndex.class);
        storage = mock(TucanaStorage.class);
        kvStore = new TucanaKeyValueStore(index, storage);
    }

    @Test
    void testPutAndGetString() {
        String key = "testKey";
        String value = "testValue";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        // Mock behavior: when getting a key, return the value as bytes
        when(index.get(keyBytes)).thenReturn(Optional.of(valueBytes));

        kvStore.putString(key, value);

        Optional<String> result = kvStore.getString(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        verify(index).upsert(eq(keyBytes), eq(valueBytes));
    }

    @Test
    void testPutAndGetInt() {
        String key = "intKey";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        int value = 42;
        byte[] valueBytes = new byte[4];
        java.nio.ByteBuffer.wrap(valueBytes).putInt(value);

        when(index.get(keyBytes)).thenReturn(Optional.of(valueBytes));

        kvStore.putInt(key, value);

        Optional<Integer> result = kvStore.getInt(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        verify(index).upsert(eq(keyBytes), any(byte[].class));
    }

    @Test
    void testRemove() {
        String key = "removeKey";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        kvStore.remove(key);

        verify(index).delete(keyBytes);
    }

    @Test
    void testCommit() {
        kvStore.commit();

        verify(storage).commit();
    }

    @Test
    void testClose() throws Exception {
        kvStore.close();

        verify(storage).close();
    }

    @Test
    void testPutFloatArray() {
        String key = "floatArray";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        float[] value = {1.0f, 2.0f, 3.0f};

        kvStore.putFloatArray(key, value);

        verify(index).upsert(eq(keyBytes), any(byte[].class));
    }

    @Test
    void testPutFloatMatrix() {
        String key = "floatMatrix";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        float[][] value = {{1.0f, 2.0f}, {3.0f, 4.0f}};

        kvStore.putFloatMatrix(key, value);

        verify(index).upsert(eq(keyBytes), any(byte[].class));
    }

    @Test
    void testGetBytes() {
        String key = "bytesKey";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] value = {0x01, 0x02, 0x03};

        when(index.get(keyBytes)).thenReturn(Optional.of(value));

        kvStore.putBytes(key, value);
        Optional<byte[]> result = kvStore.getBytes(key);

        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get());
    }

    @Test
    void testGetStringAt() {
        long offset = 1234L;
        String value = "atValue";
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        when(storage.read(offset)).thenReturn(valueBytes);

        String result = kvStore.getStringAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetIntAt() {
        long offset = 5678L;
        int value = 99;
        byte[] valueBytes = new byte[4];
        java.nio.ByteBuffer.wrap(valueBytes).putInt(value);

        when(storage.read(offset)).thenReturn(valueBytes);

        int result = kvStore.getIntAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetFloatArrayAt() {
        long offset = 9012L;
        float[] value = {1.1f, 2.2f};
        byte[] valueBytes = new byte[value.length * 4];
        java.nio.ByteBuffer.wrap(valueBytes).asFloatBuffer().put(value);

        when(storage.read(offset)).thenReturn(valueBytes);

        float[] result = kvStore.getFloatArrayAt(offset);
        assertArrayEquals(value, result);
    }

    @Test
    void testGetFloatMatrixAt() {
        long offset = 3456L;
        float[][] value = {{1.1f, 2.2f}, {3.3f, 4.4f}};
        int rows = 2, cols = 2;
        byte[] valueBytes = new byte[8 + rows * cols * 4];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(valueBytes);
        buffer.putInt(rows);
        buffer.putInt(cols);
        for (float[] row : value) {
            buffer.asFloatBuffer().put(row);
            buffer.position(buffer.position() + cols * 4);
        }

        when(storage.read(offset)).thenReturn(valueBytes);

        float[][] result = kvStore.getFloatMatrixAt(offset);
        assertArrayEquals(value[0], result[0]);
        assertArrayEquals(value[1], result[1]);
    }

    @Test
    void testGetBytesAt() {
        long offset = 7890L;
        byte[] value = {0x0A, 0x0B, 0x0C};

        when(storage.read(offset)).thenReturn(value);

        byte[] result = kvStore.getBytesAt(offset);
        assertArrayEquals(value, result);
    }

    @Test
    void testGetOffsetStream() {
        List<Long> expectedOffsets = List.of(100L, 200L, 300L);
        when(index.allOffsets()).thenReturn(expectedOffsets.stream());

        Stream<Long> offsetStream = kvStore.getOffsetStream();
        List<Long> actualOffsets = offsetStream.toList();

        assertEquals(expectedOffsets.size(), actualOffsets.size());
        assertTrue(actualOffsets.containsAll(expectedOffsets));
        verify(index).allOffsets();
    }
}
