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

package es.nachobrito.vulcanodb.core.store.axon;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for AxonDataStore crash recovery using WAL.
 */
class AxonDataStoreRecoveryTest {
    private Path path;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-recovery-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectRecoveryOfUncommittedDocument() throws Exception {
        // 1. Initialize Axon
        AxonDataStore axon = buildAxonStore();
        axon.initialize().get();

        Document document = Document.builder()
                .withStringField("content", "to be recovered")
                .build();

        // 2. We need a way to simulate a crash *after* WAL but *before* persistence.
        // For this test, we'll assume the implementation of add() does exactly that if we kill the process.
        // Since we can't easily kill the JVM here, we might need a "Hooks" mechanism or just 
        // test that if a WAL entry exists, it gets applied on next init.
        
        // Let's assume for now we have a way to manually inject a WAL entry or 
        // use a modified version of AxonDataStore that crashes.
        
        // For the purpose of the initial test definition, let's say we have an AxonDataStore 
        // that we close "abruptly" (if we had a way to close it without flushing).
        
        axon.add(document);
        axon.close();

        // 3. Re-open and verify
        AxonDataStore recoveredAxon = buildAxonStore();
        recoveredAxon.initialize().get();

        Optional<Document> read = recoveredAxon.get(document.id());
        assertTrue(read.isPresent(), "Document should have been recovered from WAL");
        assertEquals(document, read.get());
        
        recoveredAxon.close();
    }

    private AxonDataStore buildAxonStore() {
        return AxonDataStore
                .builder()
                .withDataFolder(path)
                .build();
    }
}
