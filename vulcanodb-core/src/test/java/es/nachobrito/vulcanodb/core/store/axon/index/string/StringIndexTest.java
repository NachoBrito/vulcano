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

package es.nachobrito.vulcanodb.core.store.axon.index.string;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author nacho
 */
public class StringIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void testStringIndex() throws Exception {
        try (var db = AxonDataStore.builder()
                .withDataFolder(tempDir)
                .withStringIndex("name")
                .build()) {

            db.add(Document.builder().withStringField("name", "John").build());
            db.add(Document.builder().withStringField("name", "Jane").build());
            db.add(Document.builder().withStringField("name", "John Doe").build());
            db.add(Document.builder().withStringField("name", "Mary Jane").build());

            // Equals
            var query1 = VulcanoDb.queryBuilder().isEqual("John", "name").build();
            var result1 = db.search(query1, 10);

            // Starts with
            var query2 = VulcanoDb.queryBuilder().startsWith("John", "name").build();
            var result2 = db.search(query2, 10);
            
            // Ends with
            var query3 = VulcanoDb.queryBuilder().endsWith("Jane", "name").build();
            var result3 = db.search(query3, 10);

            // Contains
            var query4 = VulcanoDb.queryBuilder().contains("n", "name").build();
            var result4 = db.search(query4, 10);

            assertEquals(1, result1.totalHits());
            assertEquals(2, result2.totalHits());
            assertEquals(2, result3.totalHits());
            assertEquals(4, result4.totalHits());


        }
    }
}
