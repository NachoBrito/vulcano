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
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
public class BigDbUseCaseTest {

    public static final String FIELD_NAME = "vector";

    @Test
    void expectSimilaritySearchWork() {
        var db = VulcanoDb.builder().build();
        var positiveCount = 1_000;
        var negativeCount = 1_000_000;
        var query = Query.builder().isSimilarTo(new float[]{1, 0}, "vector").build();

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

        int rounds = 200;
        long maxP95 = 100;
        float maxAvg = 75.0f;
        var measurements = new long[rounds];
        var results = new QueryResult[rounds];
        long start, end;

        for (int i = 0; i < measurements.length; i++) {
            start = System.currentTimeMillis();
            results[i] = db.search(query);
            end = System.currentTimeMillis();
            measurements[i] = end - start;
        }

        for (QueryResult queryResult : results) {
            assertEquals(positiveCount, queryResult.getDocuments().size());
        }

        Arrays.sort(measurements);
        int p95Index = Math.toIntExact(Math.round(0.95 * measurements.length)) - 1;
        var p95 = measurements[p95Index];
        var stats = Arrays.stream(measurements).summaryStatistics();
        var max = stats.getMax();
        var min = stats.getMin();
        var avg = stats.getAverage();

        assertTrue(p95 < maxP95, "P95 of %d is not < %d".formatted(p95, maxP95));
        assertTrue(avg < maxAvg, "avg of %f is not < %f".formatted(avg, maxAvg));
    }
}
