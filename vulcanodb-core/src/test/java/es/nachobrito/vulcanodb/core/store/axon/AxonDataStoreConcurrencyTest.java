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
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that concurrent indexing and search operations work correctly without corruption.
 *
 * @author nacho
 */
class AxonDataStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void testConcurrentInsertsAndSearches() throws Exception {
        int numThreads = 10;
        int docsPerThread = 50;
        int totalDocs = numThreads * docsPerThread;
        int vectorDim = 128;

        AxonDataStore store = AxonDataStore.builder()
                .withDataFolder(tempDir)
                .withVectorIndex("embedding", HnswConfig.builder().withDimensions(vectorDim).build())
                .build();

        store.initialize().join();

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            List<DocumentId> docIds = new ArrayList<>();
            for (int i = 0; i < totalDocs; i++) {
                docIds.add(DocumentId.of(UUID.randomUUID().toString()));
            }

            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < docsPerThread; i++) {
                        int docIdx = threadId * docsPerThread + i;
                        float[] vector = new float[vectorDim];
                        vector[0] = (float) docIdx; // Distinct vector

                        Document doc = Document.builder()
                                .withId(docIds.get(docIdx))
                                .withVectorField("embedding", vector)
                                .withStringField("name", "Document " + docIdx)
                                .build();

                        store.add(doc);
                    }
                }, executor));
            }

            // Wait for all inserts to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Verify count
            for (int i = 0; i < totalDocs; i++) {
                assertTrue(store.get(docIds.get(i)).isPresent(), "Document " + docIds.get(i) + " should exist");
            }

            // Verify search works concurrently
            List<CompletableFuture<Void>> searchFutures = new ArrayList<>();
            for (int t = 0; t < numThreads; t++) {
                searchFutures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 10; i++) {
                        float[] queryVector = new float[vectorDim];
                        queryVector[0] = 5.0f;

                        var result = store.search(
                                Query.builder().isSimilarTo(queryVector, "embedding").build(),
                                10
                        );
                        assertFalse(result.getDocuments().isEmpty());
                    }
                }, executor));
            }
            CompletableFuture.allOf(searchFutures.toArray(new CompletableFuture[0])).get();
        } finally {
            store.close();
        }
    }
}
