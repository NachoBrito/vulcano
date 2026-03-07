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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TucanaBeTreeTest {

    @TempDir
    Path tempDir;

    private TucanaStorage storage;
    private TucanaBeTree tree;
    private static final long NODE_SIZE = 4096;

    @BeforeEach
    void setUp() throws IOException {
        Path dbFile = tempDir.resolve("test.db");
        storage = new AxonTucanaStorage(dbFile);
        tree = new TucanaBeTree(storage, NODE_SIZE);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void testInitialState() {
        assertNotNull(tree);
        System.out.println("Initial State - Epoch: " + storage.currentEpoch() + ", Root: " + tree.rootOffset());
        assertTrue(tree.rootOffset() >= 0);
        assertFalse(tree.get(toBuffer("non-existent")).isPresent());
    }

    @Test
    void testUpsertAndGet() {
        ByteBuffer key = toBuffer("key1");
        long offset = 12345L;

        tree.upsert(key, offset);
        
        OptionalLong result = tree.get(key);
        assertTrue(result.isPresent());
        assertEquals(offset, result.getAsLong());
    }

    @Test
    void testDelete() {
        ByteBuffer key = toBuffer("key1");
        long offset = 12345L;

        tree.upsert(key, offset);
        assertTrue(tree.get(key).isPresent());

        tree.delete(key);
        assertFalse(tree.get(key).isPresent());
    }

    @Test
    void testMultipleUpdates() {
        ByteBuffer key = toBuffer("key1");
        
        tree.upsert(key, 100L);
        tree.upsert(key, 200L);
        tree.upsert(key, 300L);

        OptionalLong result = tree.get(key);
        assertTrue(result.isPresent());
        assertEquals(300L, result.getAsLong());
    }

    @Test
    void testPersistenceAcrossCommits() throws Exception {
        ByteBuffer key = toBuffer("persistent-key");
        long offset = 999L;

        System.out.println("--- testPersistenceAcrossCommits Start ---");
        tree.upsert(key, offset);
        System.out.println("Before commit - Epoch: " + storage.currentEpoch() + ", Root: " + tree.rootOffset() + ", Offset present: " + tree.get(key).isPresent());
        
        storage.commit();
        System.out.println("After commit - Epoch: " + storage.currentEpoch() + ", Root from storage: " + storage.getRootOffset(storage.currentEpoch()).orElse(-1L));

        // Simulate re-opening
        storage.close();
        storage = new AxonTucanaStorage(tempDir.resolve("test.db"));
        tree = new TucanaBeTree(storage, NODE_SIZE);
        System.out.println("Re-opened - Epoch: " + storage.currentEpoch() + ", Root: " + tree.rootOffset());

        OptionalLong result = tree.get(key);
        System.out.println("Final check - Result present: " + result.isPresent());
        assertTrue(result.isPresent());
        assertEquals(offset, result.getAsLong());
    }

    @Test
    void testAllOffsets() {
        tree.upsert(toBuffer("k1"), 10L);
        tree.upsert(toBuffer("k2"), 20L);
        tree.upsert(toBuffer("k3"), 30L);
        tree.delete(toBuffer("k2"));

        var offsets = tree.allOffsets().toList();
        assertEquals(2, offsets.size());
        assertTrue(offsets.contains(10L));
        assertTrue(offsets.contains(30L));
        assertFalse(offsets.contains(20L));
    }

    private ByteBuffer toBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }
}
