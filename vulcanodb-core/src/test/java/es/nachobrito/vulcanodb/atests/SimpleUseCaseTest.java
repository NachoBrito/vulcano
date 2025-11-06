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

import es.nachobrito.vulcanodb.core.domain.model.VulcanoDb;
import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
public class SimpleUseCaseTest {

    public static final String FIELD_NAME = "vector";

    @Test
    void expectSimilaritySearchWork() {
        var db = VulcanoDb.builder().build();
        var document1 = Document.builder()
                .withVectorField(FIELD_NAME, new double[]{1, 0})
                .build();

        var document2 = Document.builder()
                .withVectorField(FIELD_NAME, new double[]{0, 1})
                .build();

        db.add(document1, document2);

        var queries = new double[][]{{1, 0}, {0, 1}, {1, 1}};
        var expected = new Document[][]{{document1}, {document2}, {document1, document2}};

        for (int i = 0; i < queries.length; i++) {
            var query = Query.builder()
                    .allSimilarTo(queries[i], List.of(FIELD_NAME))
                    .build();
            var expectedResult = expected[i];

            var result = db.search(query);

            assertEquals(expectedResult.length, result.getDocuments().size());
            for (var document : expectedResult) {
                assertTrue(result.getDocuments().stream().anyMatch(it -> it.document().equals(document)));
            }
        }

    }
}
