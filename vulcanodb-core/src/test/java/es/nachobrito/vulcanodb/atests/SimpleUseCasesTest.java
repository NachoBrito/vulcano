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

package es.nachobrito.vulcanodb.atests;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
public class SimpleUseCasesTest {

    public static final String VECTOR_FIELD_NAME = "vector";
    public static final String STRING_FIELD_NAME = "string";
    public static final String INTEGER_FIELD_NAME = "integer";

    @Test
    void expectSimilaritySearchWork() {
        var db = VulcanoDb.builder().build();
        var document1 = Document.builder()
                .withVectorField(VECTOR_FIELD_NAME, new float[]{1, 0})
                .build();

        var document2 = Document.builder()
                .withVectorField(VECTOR_FIELD_NAME, new float[]{0, 1})
                .build();

        db.add(document1, document2);

        var queries = new float[][]{{1, 0}, {0, 1}, {1, 1}};
        var expected = new Document[][]{{document1}, {document2}, {document1, document2}};

        for (int i = 0; i < queries.length; i++) {
            var query = VulcanoDb.queryBuilder()
                    .allSimilarTo(queries[i], List.of(VECTOR_FIELD_NAME))
                    .build();
            var expectedResult = expected[i];

            var result = db.search(query);

            assertEquals(expectedResult.length, result.getDocuments().size());
            for (var document : expectedResult) {
                assertTrue(result.getDocuments().stream().anyMatch(it -> it.document().equals(document)));
            }
        }
    }


    @Test
    void expectSimilaritySplitSearchWork() {
        var db = VulcanoDb.builder().build();
        var document1 = Document.builder()
                .withVectorField(VECTOR_FIELD_NAME, new float[][]{{1, 0}, {0, 1}})
                .build();

        var document2 = Document.builder()
                .withVectorField(VECTOR_FIELD_NAME, new float[][]{{0, 1}, {0, 2}})
                .build();

        db.add(document1, document2);

        var queries = new float[][]{{1, 0}, {0, 1}, {1, 1}};
        var expected = new Document[][]{{document1}, {document1, document2}, {document1, document2}};

        for (int i = 0; i < queries.length; i++) {
            var query = VulcanoDb.queryBuilder()
                    .allSimilarTo(queries[i], List.of(VECTOR_FIELD_NAME))
                    .build();
            var expectedResult = expected[i];

            var result = db.search(query);

            assertEquals(expectedResult.length, result.getDocuments().size());
            for (var document : expectedResult) {
                assertTrue(result.getDocuments().stream().anyMatch(it -> it.document().equals(document)));
            }
        }
    }


    @Test
    void expectStringFieldSearchWork() {
        var db = VulcanoDb.builder().build();
        var document1 = Document.builder()
                .withStringField(STRING_FIELD_NAME, "FIRST DOCUMENT")
                .build();

        var document2 = Document.builder()
                .withStringField(STRING_FIELD_NAME, "SECOND DOCUMENT")
                .build();

        var docs = List.of(document1, document2);

        db.add(document1, document2);

        var result = db.search(VulcanoDb.queryBuilder().isEqual("FIRST DOCUMENT", STRING_FIELD_NAME).build());
        assertEquals(1, result.getDocuments().size());
        assertEquals(document1, result.getDocuments().getFirst().document());

        result = db.search(VulcanoDb.queryBuilder().startsWith("SECOND", STRING_FIELD_NAME).build());
        assertEquals(1, result.getDocuments().size());
        assertEquals(document2, result.getDocuments().getFirst().document());

        result = db.search(VulcanoDb.queryBuilder().endsWith("DOCUMENT", STRING_FIELD_NAME).build());
        assertEquals(2, result.getDocuments().size());
        var resultDocs1 = result.getDocuments().stream().map(ResultDocument::document).toList();
        docs.forEach(it -> assertTrue(resultDocs1.contains(it)));

        result = db.search(VulcanoDb.queryBuilder().contains("DOC", STRING_FIELD_NAME).build());
        assertEquals(2, result.getDocuments().size());
        var resultDocs2 = result.getDocuments().stream().map(ResultDocument::document).toList();
        docs.forEach(it -> assertTrue(resultDocs2.contains(it)));
    }

    @Test
    void expectIntegerFieldSearchWork() {
        var db = VulcanoDb.builder().build();
        var document1 = Document.builder()
                .withIntegerField(INTEGER_FIELD_NAME, 100)
                .build();

        var document2 = Document.builder()
                .withIntegerField(INTEGER_FIELD_NAME, 200)
                .build();

        var docs = List.of(document1, document2);

        db.add(document1, document2);

        var result = db.search(VulcanoDb.queryBuilder().isEqual(100, INTEGER_FIELD_NAME).build());
        assertEquals(1, result.getDocuments().size());
        assertEquals(document1, result.getDocuments().getFirst().document());

        result = db.search(VulcanoDb.queryBuilder().isGreaterThan(100, INTEGER_FIELD_NAME).build());
        assertEquals(1, result.getDocuments().size());
        assertEquals(document2, result.getDocuments().getFirst().document());

        result = db.search(VulcanoDb.queryBuilder().isGreaterThanOrEqual(100, INTEGER_FIELD_NAME).build());
        assertEquals(2, result.getDocuments().size());
        var resultDocs1 = result.getDocuments().stream().map(ResultDocument::document).toList();
        docs.forEach(it -> assertTrue(resultDocs1.contains(it)));

        result = db.search(VulcanoDb.queryBuilder().isLessThan(200, INTEGER_FIELD_NAME).build());
        assertEquals(1, result.getDocuments().size());
        assertEquals(document1, result.getDocuments().getFirst().document());

        result = db.search(VulcanoDb.queryBuilder().isLessThanOrEqual(200, INTEGER_FIELD_NAME).build());
        assertEquals(2, result.getDocuments().size());
        var resultDocs2 = result.getDocuments().stream().map(ResultDocument::document).toList();
        docs.forEach(it -> assertTrue(resultDocs2.contains(it)));

    }
}
