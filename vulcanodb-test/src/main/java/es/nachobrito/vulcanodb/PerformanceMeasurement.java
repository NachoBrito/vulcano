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

package es.nachobrito.vulcanodb;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.query.Query;

/**
 * @author nacho
 */
public class PerformanceMeasurement {
    public static final String FIELD_NAME = "vector";

    static void main(String[] args) {
        var db = VulcanoDb.builder().build();
        var positiveCount = 1_000;
        var negativeCount = 1_000_000;
        var query = Query.builder()
                .isSimilarTo(new float[]{1, 0}, "vector")
                .build();

        for (int i = 0; i < positiveCount; i++) {
            var document = Document.builder()
                    .withVectorField(FIELD_NAME, new float[]{1, 0})
                    .build();
            db.add(document);
        }

        for (int i = 0; i < negativeCount; i++) {
            var document = Document.builder()
                    .withVectorField(FIELD_NAME, new float[]{0, 1})
                    .build();
            db.add(document);
        }
        for (int i = 0; i < 100; i++) {
            db.search(query);
        }
    }
}
