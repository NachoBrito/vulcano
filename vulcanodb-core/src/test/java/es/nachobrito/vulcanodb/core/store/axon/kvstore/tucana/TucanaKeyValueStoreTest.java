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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        byte[] entryBytes = Entry.of(key, value).array();
        long offset = 100L;

        // Mock behavior: when getting a key, return the offset, then read from storage
        when(storage.write(entryBytes)).thenReturn(offset);
        when(index.get(any(ByteBuffer.class))).thenReturn(OptionalLong.of(offset));
        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        kvStore.putString(key, value);

        Optional<String> result = kvStore.getString(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        verify(storage).write(eq(entryBytes));
        verify(index).upsert(eq(keyBuffer), eq(offset));
    }

    @Test
    void testPutAndGetInt() {
        String key = "intKey";
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        int value = 42;
        byte[] entryBytes = Entry.of(key, value).array();
        long offset = 200L;

        when(storage.write(entryBytes)).thenReturn(offset);
        when(index.get(any(ByteBuffer.class))).thenReturn(OptionalLong.of(offset));
        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        kvStore.putInt(key, value);

        Optional<Integer> result = kvStore.getInt(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        verify(storage).write(eq(entryBytes));
        verify(index).upsert(eq(keyBuffer), eq(offset));
    }

    @Test
    void testRemove() {
        String key = "removeKey";
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));

        kvStore.remove(key);

        verify(index).delete(eq(keyBuffer));
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
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        float[] value = {1.0f, 2.0f, 3.0f};

        kvStore.putFloatArray(key, value);

        verify(storage).write(any(byte[].class));
        verify(index).upsert(eq(keyBuffer), anyLong());
    }

    @Test
    void testPutFloatMatrix() {
        String key = "floatMatrix";
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        float[][] value = {{1.0f, 2.0f}, {3.0f, 4.0f}};

        kvStore.putFloatMatrix(key, value);

        verify(storage).write(any(byte[].class));
        verify(index).upsert(eq(keyBuffer), anyLong());
    }

    @Test
    void testGetBytes() {
        String key = "bytesKey";
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        byte[] value = {0x01, 0x02, 0x03};
        long offset = 300L;

        when(storage.write(any(byte[].class))).thenReturn(offset);
        when(index.get(any(ByteBuffer.class))).thenReturn(OptionalLong.of(offset));
        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(value));

        kvStore.putBytes(key, value);
        Optional<byte[]> result = kvStore.getBytes(key);

        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get());
    }

    @Test
    void testGetStringAt() {
        long offset = 1234L;
        String key = "key";
        String value = "atValue";
        byte[] entryBytes = Entry.of(key, value).array();

        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        String result = kvStore.getStringAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetIntAt() {
        long offset = 5678L;
        String key = "key";
        int value = 99;
        byte[] entryBytes = Entry.of(key, value).array();

        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        int result = kvStore.getIntAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetFloatArrayAt() {
        long offset = 9012L;
        String key = "key";
        float[] value = {1.1f, 2.2f};
        byte[] entryBytes = Entry.of(key, value).array();

        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        float[] result = kvStore.getFloatArrayAt(offset);
        assertArrayEquals(value, result);
    }

    @Test
    void testGetFloatMatrixAt() {
        long offset = 3456L;
        String key = "key";
        float[][] value = {{1.1f, 2.2f}, {3.3f, 4.4f}};
        byte[] entryBytes = Entry.of(key, value).array();

        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(entryBytes));

        float[][] result = kvStore.getFloatMatrixAt(offset);
        assertArrayEquals(value[0], result[0]);
        assertArrayEquals(value[1], result[1]);
    }

    @Test
    void testGetBytesAt() {
        long offset = 7890L;
        byte[] value = {0x0A, 0x0B, 0x0C};

        when(storage.read(offset)).thenReturn(ByteBuffer.wrap(value));

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
