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

package es.nachobrito.vulcanodb.core.store.axon.wal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalLogBoundaryTest {

    private Path tempDir;
    private WalLog walLog;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-log-test");
        walLog = new WalLog(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (walLog != null) {
            walLog.close();
        }
        es.nachobrito.vulcanodb.core.util.FileUtils.deleteRecursively(tempDir.toFile());
    }

    @Test
    void testEntryNearBoundary() throws Exception {
        // SEGMENT_SIZE is 64MB. Let's write entries until we are near the boundary.
        // Each entry: 4 (len) + 4 (status) + 8 (txId) + payload.length
        // Aligned to 8 bytes.
        
        byte[] largePayload = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largePayload, (byte) 1);
        
        // Write 63 entries of 1MB
        for (int i = 0; i < 63; i++) {
            walLog.append(i, largePayload);
        }
        
        // Now we have ~63MB used.
        // Let's write another entry that would cross the boundary if not handled.
        // Size of 1MB entry is > 1MB. 63 * ~1MB is close to 64MB.
        
        // Let's be more precise to hit the boundary.
        // Current writeOffset should be around 63 * (16 + 1024*1024)
        // 16 + 1024*1024 = 1,048,592 bytes.
        // 63 * 1,048,592 = 66,061,296 bytes.
        // 64MB = 67,108,864 bytes.
        // Remaining in segment: 67,108,864 - 66,061,296 = 1,047,568 bytes.
        
        // A 1MB payload (1,048,576 bytes) + 16 bytes overhead = 1,048,592 bytes.
        // This will NOT fit in the remaining 1,047,568 bytes.
        
        walLog.append(63, largePayload); // This should trigger the boundary logic and NOT throw exception
        
        assertEquals(64, walLog.uncommittedStream().count());
    }
}
