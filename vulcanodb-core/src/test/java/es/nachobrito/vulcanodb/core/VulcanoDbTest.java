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

package es.nachobrito.vulcanodb.core;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentMother;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author nacho
 */
class VulcanoDbTest {
    private Path path;
    private VulcanoDb vulcanoDb;


    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        var axon = buildAxonStore();
        vulcanoDb = VulcanoDb.builder().withDataStore(axon).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        vulcanoDb.close();
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    public void expectDocumentCountUpdates() throws InterruptedException {
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "number1", 0,
                        "number2", 0
                )).build();
        var shape = exampleDoc.getShape();
        var docs = DocumentMother.random(shape, 100);
        docs.forEach(vulcanoDb::add);
        Thread.sleep(500);
        assertEquals(100, vulcanoDb.getDocumentCount().intValue());

        vulcanoDb.add(exampleDoc);
        Thread.sleep(500);
        assertEquals(101, vulcanoDb.getDocumentCount().intValue());

        vulcanoDb.remove(exampleDoc.id());
        Thread.sleep(500);
        assertEquals(100, vulcanoDb.getDocumentCount().intValue());
    }


    private AxonDataStore buildAxonStore() {
        return AxonDataStore
                .builder()
                .withDataFolder(path)
                .build();
    }
}