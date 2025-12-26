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
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
public class QueryTest extends AxonDataStoreTest {

    @Test
    public void expectLeafQueriesWork() {
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "field1", "This is an example document",
                        "field2", "With two string fields"
                )).build();

        var shape = exampleDoc.getShape();

        var docs = DocumentMother.random(shape, 100);
        var axon = getAxon();
        var futures = docs.stream().map(axon::addAsync).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        var query = Query.builder().contains("example", "field1").build();
        var result = axon.search(query);
        assertTrue(result.getDocuments().isEmpty());

        axon.add(exampleDoc);
        var result2 = axon.search(query);
        assertEquals(1, result2.getDocuments().size());
        assertEquals(exampleDoc, result2.getDocuments().getFirst().document());
        assertEquals(1.0f, result2.getDocuments().getFirst().score());
    }
}
