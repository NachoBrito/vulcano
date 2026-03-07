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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AxonTucanaStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void testBasicReadWrite() throws Exception {
        Path storagePath = tempDir.resolve("tucana.db");
        try (AxonTucanaStorage storage = new AxonTucanaStorage(storagePath)) {
            byte[] data = "Hello Tucana".getBytes();
            long offset = storage.write(data);
            
            ByteBuffer readBuffer = storage.read(offset);
            byte[] readData = new byte[readBuffer.remaining()];
            readBuffer.get(readData);
            
            assertArrayEquals(data, readData);
        }
    }

    @Test
    void testCommitAndPersistence() throws Exception {
        Path storagePath = tempDir.resolve("tucana_persistence.db");
        long offset;
        
        try (AxonTucanaStorage storage = new AxonTucanaStorage(storagePath)) {
            byte[] data = "Persistent Data".getBytes();
            offset = storage.write(data);
            storage.setRootOffset(12345L);
            storage.commit();
            assertEquals(1, storage.currentEpoch());
        }
        
        // Reopen storage
        try (AxonTucanaStorage storage = new AxonTucanaStorage(storagePath)) {
            assertEquals(1, storage.currentEpoch());
            assertEquals(Optional.of(12345L), storage.getRootOffset(1));
            
            ByteBuffer readBuffer = storage.read(offset);
            byte[] readData = new byte[readBuffer.remaining()];
            readBuffer.get(readData);
            assertArrayEquals("Persistent Data".getBytes(), readData);
        }
    }

    @Test
    void testRollback() throws Exception {
        Path storagePath = tempDir.resolve("tucana_rollback.db");
        
        try (AxonTucanaStorage storage = new AxonTucanaStorage(storagePath)) {
            storage.write("Initial Data".getBytes());
            storage.commit(); // Epoch 1
            
            long preRollbackOffset = storage.write("Dirty Data".getBytes());
            storage.rollback();
            
            // Writing again should reuse the offset if rollback worked (linear allocator)
            long postRollbackOffset = storage.write("Clean Data".getBytes());
            assertEquals(preRollbackOffset, postRollbackOffset);
        }
    }

    @Test
    void testMultiplePages() throws Exception {
        Path storagePath = tempDir.resolve("tucana_pages.db");
        try (AxonTucanaStorage storage = new AxonTucanaStorage(storagePath)) {
            // Write more than 1MB of data to trigger multiple pages
            byte[] largeData = new byte[2 * 1024 * 1024]; // 2MB
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            
            long offset = storage.write(largeData);
            storage.commit();
            
            ByteBuffer readBuffer = storage.read(offset);
            assertEquals(largeData.length, readBuffer.remaining());
            for (int i = 0; i < largeData.length; i++) {
                assertEquals(largeData[i], readBuffer.get());
            }
        }
    }
}
