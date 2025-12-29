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
import es.nachobrito.vulcanodb.core.util.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
class AxonDataStoreTest {
    private Path path;
    private AxonDataStore axon;


    AxonDataStore getAxon() {
        return axon;
    }

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        axon = buildAxonStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        axon.close();
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    public void expectWritesAndReadsDocuments() {
        var now = ZonedDateTime.now();
        Map<String, Object> fields = Map.of(
                "integer", 1,
                "string", "a string",
                "vector1", new float[]{1.0f, 2.0f},
                "vector2", new Float[]{1.0f, 2.0f},
                "matrix", new float[][]{{1, 2}, {3, 4}},
                "date", now
        );

        var document1 = Document.builder().with(fields).build();
        var document2 = Document.builder().with(fields).build();

        //Synchronously
        axon.add(document1);
        var read = axon.get(document1.id());
        assertTrue(read.isPresent());
        assertEquals(document1, read.get());

        //Asynchronously
        axon
                .addAsync(document2)
                .thenCompose(_ -> axon.getAsync(document2.id()))
                .thenApply(doc -> {
                    assertTrue(doc.isPresent());
                    assertEquals(document2, doc.get());
                    return null;
                });
    }

    private AxonDataStore buildAxonStore() {
        var properties = new Properties();
        properties.setProperty(ConfigProperties.PROPERTY_PATH, path.toString());
        return AxonDataStore
                .builder()
                .withDocumentWriter(new DefaultDocumentPersister(new TypedProperties(properties)))
                .build();
    }
}