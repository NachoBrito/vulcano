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

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WalManager.
 */
class WalManagerTest {

    private Path tempDir;
    private WalManager walManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-test");
        walManager = createWalManager(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (walManager != null) {
            walManager.close();
        }
        FileUtils.deleteRecursively(tempDir.toFile());
    }

    protected WalManager createWalManager(Path path) throws IOException {
        return new DefaultWalManager(path);
    }

    @Test
    void expectRecordsAndReadsUncommittedAdds() throws IOException {
        Document doc = Document.builder()
                .withStringField("key", "value")
                .build();

        long txId = walManager.recordAdd(doc);
        assertTrue(txId > 0);

        List<WalEntry> uncommitted = walManager.readUncommitted();
        assertEquals(1, uncommitted.size());
        WalEntry entry = uncommitted.get(0);
        assertEquals(txId, entry.txId());
        assertEquals(WalEntry.Type.ADD, entry.type());
        assertEquals(doc, entry.document().orElseThrow());
        assertFalse(entry.committed());
    }

    @Test
    void expectCommitRemovesFromUncommitted() throws IOException {
        Document doc = Document.builder()
                .withStringField("key", "value")
                .build();

        long txId = walManager.recordAdd(doc);
        walManager.commit(txId);

        List<WalEntry> uncommitted = walManager.readUncommitted();
        assertTrue(uncommitted.isEmpty(), "Committed transactions should not be in uncommitted list");
    }

    @Test
    void expectRecordsAndReadsComplexTypes() throws IOException {
        float[] vector = {1.1f, 2.2f, 3.3f};
        float[][] matrix = {{1f, 2f}, {3f, 4f}};
        Document doc = Document.builder()
                .withStringField("s", "val")
                .withIntegerField("i", 42)
                .withVectorField("v", vector)
                .withVectorField("m", matrix)
                .build();

        long txId = walManager.recordAdd(doc);
        List<WalEntry> uncommitted = walManager.readUncommitted();
        assertEquals(1, uncommitted.size());
        Document read = uncommitted.get(0).document().orElseThrow();
        assertEquals("val", read.field("s").orElseThrow().value());
        assertEquals(42, read.field("i").orElseThrow().value());
        assertArrayEquals(vector, (float[]) read.field("v").orElseThrow().value());
        assertArrayEquals(matrix, (float[][]) read.field("m").orElseThrow().value());
    }

    @Test
    void expectRecoveryAfterCrash() throws Exception {
        Document doc = Document.builder()
                .withStringField("key", "value")
                .build();

        long txId = walManager.recordAdd(doc);
        // Simulate crash by closing without committing
        walManager.close();

        // Reopen
        walManager = createWalManager(tempDir);
        List<WalEntry> uncommitted = walManager.readUncommitted();
        assertEquals(1, uncommitted.size());
        assertEquals(txId, uncommitted.get(0).txId());
    }

    @Test
    void expectCheckpointTruncatesLog() throws Exception {
        Document doc = Document.builder().withStringField("k", "v").build();
        long txId = walManager.recordAdd(doc);
        walManager.commit(txId);

        walManager.checkpoint();

        // After checkpoint, even if we reopen, the log should be clean if all were committed
        walManager.close();
        walManager = createWalManager(tempDir);
        assertTrue(walManager.readUncommitted().isEmpty());
    }
}
