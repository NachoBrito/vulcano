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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TucanaKeyValueStoreTest {

    @TempDir
    Path tempDir;

    private TucanaKeyValueStore store;

    @BeforeEach
    void setUp() throws IOException {
        store = new TucanaKeyValueStore(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
    }

    @Test
    void testPutAndGetString() {
        store.putString("key1", "value1");
        Optional<String> result = store.getString("key1");

        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void testPutAndGetInt() {
        store.putInt("keyInt", 42);
        Optional<Integer> result = store.getInt("keyInt");

        assertTrue(result.isPresent());
        assertEquals(42, result.get());
    }

    @Test
    void testPutAndGetFloatArray() {
        float[] values = new float[]{1.1f, 2.2f, 3.3f};
        store.putFloatArray("keyFloat", values);
        Optional<float[]> result = store.getFloatArray("keyFloat");

        assertTrue(result.isPresent());
        assertArrayEquals(values, result.get());
    }

    @Test
    void testRecovery() throws Exception {
        store.putString("recoveryKey", "recoveryValue");
        store.commit();
        store.close();

        // Re-open the store
        TucanaKeyValueStore newStore = new TucanaKeyValueStore(tempDir);
        Optional<String> result = newStore.getString("recoveryKey");

        assertTrue(result.isPresent());
        assertEquals("recoveryValue", result.get());
        newStore.close();
    }

    @Test
    void testRemove() {
        store.putString("keyToRemove", "value");
        store.remove("keyToRemove");
        
        Optional<String> result = store.getString("keyToRemove");
        assertFalse(result.isPresent());
    }
}
