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

import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.index.TucanaBeTree;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.AxonTucanaStorage;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.TucanaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TucanaKeyValueStore}.
 * These tests follow the TDD approach and are expected to fail initially.
 */
class TucanaKeyValueStoreTest {

    @TempDir
    Path tempDir;

    private TucanaKeyValueStore kvStore;
    private static final long NODE_SIZE = 4096;

    @BeforeEach
    void setUp() {
        Path storagePath = tempDir.resolve("vulcano.db");
        TucanaStorage storage = new AxonTucanaStorage(storagePath);
        TucanaIndex index = new TucanaBeTree(storage, NODE_SIZE);

        kvStore = new TucanaKeyValueStore(index, storage);
    }

    @Test
    void testPutAndGetString() {
        String key = "testKey";
        String value = "testValue";

        kvStore.putString(key, value);
        Optional<String> result = kvStore.getString(key);

        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }

    @Test
    void testPutAndGetInt() {
        String key = "intKey";
        int value = 42;
        kvStore.putInt(key, value);

        Optional<Integer> result = kvStore.getInt(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

    }

    @Test
    void testRemove() {
        String key = "intKey";
        int value = 42;
        kvStore.putInt(key, value);

        Optional<Integer> result = kvStore.getInt(key);
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        kvStore.remove(key);
        result = kvStore.getInt(key);
        assertTrue(result.isEmpty());
    }

    @Test
    void testPutFloatArray() {
        String key = "floatArray";
        ByteBuffer keyBuffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
        float[] value = {1.0f, 2.0f, 3.0f};

        kvStore.putFloatArray(key, value);

        var result = kvStore.getFloatArray(key);
        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get());
    }

    @Test
    void testPutFloatMatrix() {
        String key = "floatMatrix";
        float[][] value = {{1.0f, 2.0f}, {3.0f, 4.0f}};

        kvStore.putFloatMatrix(key, value);
        var result = kvStore.getFloatMatrix(key);
        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get());
    }

    @Test
    void testGetBytes() {
        String key = "bytesKey";
        byte[] value = {0x01, 0x02, 0x03};

        kvStore.putBytes(key, value);
        Optional<byte[]> result = kvStore.getBytes(key);

        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get());
    }

    @Test
    void testGetStringAt() {
        String key = "stringAtKey";
        String value = "atValue";

        var offset = kvStore.putString(key, value);

        String result = kvStore.getStringAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetIntAt() {
        String key = "key";
        int value = 99;
        var offset = kvStore.putInt(key, value);
        int result = kvStore.getIntAt(offset);
        assertEquals(value, result);
    }

    @Test
    void testGetFloatArrayAt() {
        String key = "key";
        float[] value = {1.1f, 2.2f};
        var offset = kvStore.putFloatArray(key, value);
        float[] result = kvStore.getFloatArrayAt(offset);
        assertArrayEquals(value, result);
    }

    @Test
    void testGetFloatMatrixAt() {
        String key = "key";
        float[][] value = {{1.1f, 2.2f}, {3.3f, 4.4f}};
        var offset = kvStore.putFloatMatrix(key, value);
        float[][] result = kvStore.getFloatMatrixAt(offset);
        assertArrayEquals(value[0], result[0]);
        assertArrayEquals(value[1], result[1]);
    }

    @Test
    void testGetBytesAt() {
        var key = "key";
        byte[] value = {0x0A, 0x0B, 0x0C};

        var offset = kvStore.putBytes(key, value);
        byte[] result = kvStore.getBytesAt(offset);
        assertArrayEquals(value, result);
    }

    @Test
    void testGetOffsetStream() {

        var intValue = 43;
        var stringValue = "stringValue";
        var floatArrayValue = new float[]{1.0f, 2.0f, 3.0f};

        var intOffset = kvStore.putInt("intOffset", intValue);
        var stringOffset = kvStore.putString("stringOffset", stringValue);
        var floatArrayOffset = kvStore.putFloatArray("floatArrayOffset", floatArrayValue);

        Stream<Long> offsetStream = kvStore.getOffsetStream();
        List<Long> actualOffsets = offsetStream.toList();

        assertEquals(3, actualOffsets.size());
        assertTrue(actualOffsets.containsAll(List.of(intOffset, stringOffset, floatArrayOffset)));
    }
}
