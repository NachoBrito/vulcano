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

import static org.junit.jupiter.api.Assertions.*;

class BεTreeTest {

    @TempDir
    Path tempDir;

    private StorageManager storageManager;
    private DataLog dataLog;
    private BεTree tree;

    @BeforeEach
    void setUp() throws IOException {
        storageManager = new StorageManager(tempDir, 1024 * 1024);
        dataLog = new DataLog(storageManager, 0);
        tree = new BεTree(storageManager, dataLog, 0);
    }

    @AfterEach
    void tearDown() {
        storageManager.close();
    }

    @Test
    void testInsertAndSearch() {
        String key = "testKey";
        long dataOffset = 12345L;

        tree.insert(key, dataOffset);
        long resultOffset = tree.search(key);

        assertEquals(dataOffset, resultOffset);
    }

    @Test
    void testSearchNonExistent() {
        long resultOffset = tree.search("noKey");
        assertEquals(-1, resultOffset);
    }

    @Test
    void testMultipleInserts() {
        tree.insert("key1", 100);
        tree.insert("key2", 200);
        tree.insert("key3", 300);

        assertEquals(100, tree.search("key1"));
        assertEquals(200, tree.search("key2"));
        assertEquals(300, tree.search("key3"));
    }

    @Test
    void testCoWOnInsert() {
        storageManager.incrementEpoch(); // Start new epoch
        long oldRoot = tree.getRootOffset();

        tree.insert("newKey", 400);

        long newRoot = tree.getRootOffset();
        assertNotEquals(oldRoot, newRoot);
        assertEquals(400, tree.search("newKey"));
    }

    @Test
    void testNodeSplitting() {
        // Force split by inserting more than MAX_LEAF_ENTRIES
        for (int i = 0; i < 200; i++) {
            String key = String.format("key%03d", i);
            long offset = dataLog.write(key, new byte[0], (byte) 0);
            tree.insert(key, offset);
        }

        // Verify all can be found
        for (int i = 0; i < 200; i++) {
            String key = String.format("key%03d", i);
            assertNotEquals(-1, tree.search(key), "Could not find " + key);
        }
    }
}
