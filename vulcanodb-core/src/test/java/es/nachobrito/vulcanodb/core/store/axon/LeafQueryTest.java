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
import es.nachobrito.vulcanodb.core.document.DocumentMother;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import es.nachobrito.vulcanodb.core.util.TypedProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
public class LeafQueryTest {
    private static Path path;
    private static AxonDataStore axon;

    private final Map<Query, Document> examplesWithEmptyResult = Map.of(
            Query.builder().isEqual("This is an example of equals", "text1").build(),
            Document.builder().withStringField("text1", "This is an example of equals").build(),

            Query.builder().contains("example of contains", "text1").build(),
            Document.builder().withStringField("text1", "This is an example of contains").build(),

            Query.builder().startsWith("Document starting", "text1").build(),
            Document.builder().withStringField("text1", "Document starting with...").build(),

            Query.builder().endsWith("ending with...", "text1").build(),
            Document.builder().withStringField("text1", "Document ending with...").build(),

            Query.builder().isEqual(-1977, "number1").build(),
            Document.builder().withIntegerField("number1", -1977).build()
    );

    private final Map<Query, Document> examplesWithoutEmptyResult = Map.of(
            Query.builder().isGreaterThan(-5, "number1").build(),
            Document.builder().withIntegerField("number1", -4).build(),

            Query.builder().isGreaterThanOrEqual(-6, "number1").build(),
            Document.builder().withIntegerField("number1", -6).build(),

            Query.builder().isLessThan(-7, "number1").build(),
            Document.builder().withIntegerField("number1", -8).build(),

            Query.builder().isLessThanOrEqual(-7, "number1").build(),
            Document.builder().withIntegerField("number1", -7).build()
    );

    @BeforeAll
    static void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        axon = buildAxonStore();
    }

    @AfterAll
    static void tearDown() throws Exception {
        axon.close();
        FileUtils.deleteRecursively(path.toFile());
    }

    private static AxonDataStore buildAxonStore() {
        var properties = new Properties();
        properties.setProperty(ConfigProperties.PROPERTY_PATH, path.toString());
        var axon = AxonDataStore
                .builder()
                .withDocumentWriter(new DefaultDocumentPersister(new TypedProperties(properties)))
                .build();
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "number1", 0,
                        "number2", 0,
                        "text1", "",
                        "text2", ""
                )).build();

        var shape = exampleDoc.getShape();
        var docs = DocumentMother.random(shape, 100);
        var futures = docs.stream().map(axon::addAsync).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        return axon;
    }

    @Test
    public void expectLeafQueriesWork() {
        examplesWithEmptyResult.forEach(LeafQueryTest::testExampleWithEmptyResult);
        examplesWithoutEmptyResult.forEach(LeafQueryTest::testExampleWithoutEmptyResult);
    }

    private static void testExampleWithoutEmptyResult(Query query, Document document) {
        //The query does not return any result before inserting the document
        var result = axon.search(query);
        assertFalse(result.getDocuments().contains(new ResultDocument(document, 1.0f)));

        //When the document is inserted, the same query returns a valid result.
        axon.add(document);
        var result2 = axon.search(query);
        assertFalse(result2.getDocuments().isEmpty());
        assertTrue(result2.getDocuments().contains(new ResultDocument(document, 1.0f)));
        assertEquals(1.0f, result2.getDocuments().getFirst().score());
    }

    private static void testExampleWithEmptyResult(Query query, Document document) {
        //The query does not return any result before inserting the document
        var result = axon.search(query);
        assertTrue(result.getDocuments().isEmpty());

        //When the document is inserted, the same query returns a valid result.
        axon.add(document);
        var result2 = axon.search(query);
        assertFalse(result2.getDocuments().isEmpty());
        assertTrue(result2.getDocuments().contains(new ResultDocument(document, 1.0f)));
        assertEquals(1.0f, result2.getDocuments().getFirst().score());
    }
}
