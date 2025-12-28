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
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentMother;
import es.nachobrito.vulcanodb.core.query.Query;
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

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author nacho
 */
public class IndexQueryTest {
    private static Path path;
    private static AxonDataStore axon;

    @BeforeAll
    static void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        axon = buildAxonStore();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (axon != null) {
            axon.close();
        }
        FileUtils.deleteRecursively(path.toFile());
    }

    private static AxonDataStore buildAxonStore() {
        var properties = new Properties();
        properties.setProperty(ConfigProperties.PROPERTY_PATH, path.toString());
        var axon = AxonDataStore
                .builder()
                .withDocumentWriter(new DefaultDocumentPersister(new TypedProperties(properties)))
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
        return axon;
    }

    @Test
    void expectIndexIsUsed() {
        var queryVector1 = Embedding.of("Java");
        var query1 = Query.builder().isSimilarTo(queryVector1, "indexedVector").build();
        var result1 = axon.search(query1);
        assertFalse(result1.getDocuments().isEmpty());

        var queryVector2 = Embedding.of("revolutionary dance");
        var query2 = Query.builder().isSimilarTo(queryVector2, "indexedVector").build();
        var result2 = axon.search(query2);
        assertFalse(result2.getDocuments().isEmpty());
    }
}
