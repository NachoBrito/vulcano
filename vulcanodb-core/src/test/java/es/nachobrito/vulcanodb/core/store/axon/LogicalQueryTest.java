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
import es.nachobrito.vulcanodb.core.query.QueryOperator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
public class LogicalQueryTest {
    private static Path path;
    private static AxonDataStore axon;

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
                        "number2", 0
                )).build();

        var shape = exampleDoc.getShape();
        var docs = DocumentMother.random(shape, 100);
        var futures = docs.stream().map(axon::addAsync).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        return axon;
    }

    @Test
    public void expectAndQueriesWork() {
        var query1 = Query
                .builder()
                .isGreaterThanOrEqual(0, "number1")
                .isGreaterThanOrEqual(0, "number2")
                .build();

        var result1 = axon.search(query1);
        assertEquals(100, result1.getDocuments().size());

        var query2 = Query
                .builder()
                .isLessThan(0, "number1")
                .isGreaterThanOrEqual(0, "number2")
                .build();

        var result2 = axon.search(query2);
        assertTrue(result2.getDocuments().isEmpty());
    }

    @Test
    public void expectOrQueriesWork() {
        var query1 = Query
                .builder()
                .isLessThan(0, "number1")
                .isGreaterThanOrEqual(0, "number2")
                .withOperator(QueryOperator.OR)
                .build();

        var result1 = axon.search(query1);
        assertEquals(100, result1.getDocuments().size());

        var query2 = Query
                .builder()
                .isLessThan(0, "number1")
                .isLessThan(0, "number2")
                .withOperator(QueryOperator.OR)
                .build();

        var result2 = axon.search(query2);
        assertTrue(result2.getDocuments().isEmpty());
    }

    @Test
    public void expectNotQueriesWork() {
        var query1 = Query
                .builder()
                .isLessThan(0, "number1")
                .isGreaterThanOrEqual(0, "number2")
                .withOperator(QueryOperator.OR)
                .build();

        var result1 = axon.search(query1);
        assertEquals(100, result1.getDocuments().size());

        var query2 = Query
                .builder()
                .not(query1)
                .build();

        var result2 = axon.search(query2);
        assertTrue(result2.getDocuments().isEmpty());
    }

}
