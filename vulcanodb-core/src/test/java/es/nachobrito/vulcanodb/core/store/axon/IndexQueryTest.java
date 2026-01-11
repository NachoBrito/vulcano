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

import es.nachobrito.vulcanodb.core.Embedding;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentMother;
import es.nachobrito.vulcanodb.core.query.similarity.VectorSimilarity;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
public class IndexQueryTest {
    private static Path path;
    private static AxonDataStore axon;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        axon = buildAxonStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (axon != null) {
            axon.close();
        }
        FileUtils.deleteRecursively(path.toFile());
    }

    private static AxonDataStore buildAxonStore() {
        var axon = AxonDataStore
                .builder()
                .withDataFolder(path)
                .withVectorIndex("indexedVector")
                .build();
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "indexedVector", new float[0]
                )).build();

        var shape = exampleDoc.getShape();
        var docs = DocumentMother.random(shape, 100);
        var futures = docs.stream().map(axon::addAsync).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        LoggerFactory.getLogger(IndexQueryTest.class).info("*** {} documents indexed.", docs.size());
        return axon;
    }

    @Test
    void expectIndexIsUsed() throws InterruptedException {
        var txt = """
                Computers need step-by-step instructions to operate. These instructions come through programs that 
                represent the algorithms that the computer needs to follow. Similar to the routines and patterns in 
                real-life, there is an order of steps with decisions and repeated patterns.
                """;
        var txtEmbedding = Embedding.of(txt);
        var doc = Document.builder()
                .withVectorField("indexedVector", txtEmbedding)
                .withStringField("indexedVector_original", txt)
                .build();
        axon.add(doc);

        var queryVector1 = Embedding.of("What is an algorithm?");
        var querySimilarity = VectorSimilarity.getDefault().between(txtEmbedding, queryVector1);

        // the score is calculated as the average of all the matching query nodes. In this case there is one
        // HNSW index that will produce querySimilarity, plus one match-all node that will produce a 1.0 score. The
        // average of those two values is the expected score for the best result.
        var expectedScore = (1.0f + querySimilarity) / 2.0f;

        var query1 = VulcanoDb.queryBuilder().isSimilarTo(queryVector1, "indexedVector").build();
        var result1 = axon.search(query1);
        //give logs time to flush
        Thread.sleep(250);
        assertFalse(result1.getDocuments().isEmpty());
        assertEquals(doc, result1.getDocuments().getFirst().document());
        assertEquals(expectedScore, result1.getDocuments().getFirst().score());
    }

    @Test
    void expectDeletedDocumentsNotReturned() throws InterruptedException {
        var txt = """
                Computers need step-by-step instructions to operate. These instructions come through programs that 
                represent the algorithms that the computer needs to follow. Similar to the routines and patterns in 
                real-life, there is an order of steps with decisions and repeated patterns.
                """;
        var txtEmbedding = Embedding.of(txt);
        var doc = Document.builder()
                .withVectorField("indexedVector", txtEmbedding)
                .build();
        axon.add(doc);

        var queryVector1 = Embedding.of("What is an algorithm?");
        var query1 = VulcanoDb.queryBuilder().isSimilarTo(queryVector1, "indexedVector").build();
        assertTrue(axon.search(query1).getDocuments().stream().anyMatch(it -> it.document().equals(doc)));

        axon.remove(doc.id());
        assertFalse(axon.search(query1).getDocuments().stream().anyMatch(it -> it.document().equals(doc)));
    }
}
