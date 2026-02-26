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
import java.util.Optional;

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
}
