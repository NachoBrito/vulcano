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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation;

import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.DocumentMatcher;
import org.roaringbitmap.longlong.LongIterator;

/**
 * @author nacho
 */
public class VectorizedRunner {
    private static final int BATCH_SIZE = 1024; // Fits well in L1/L2 cache

    public QueryResult run(
            DocIdSet candidates,
            DocumentMatcher residualMatcher,
            ExecutionContext ctx
    ) {
        var builder = QueryResult.builder();
        LongIterator it = candidates.iterator();

        long[] batchIds = new long[BATCH_SIZE];

        while (it.hasNext()) {
            // 1. Fill a batch with candidate IDs from the Bitmap
            int count = 0;
            while (count < BATCH_SIZE && it.hasNext()) {
                batchIds[count++] = it.next();
            }

            // 2. Apply Residual Filtering
            // We only keep IDs that pass the scan-based predicates
            int survivingCount = 0;
            long[] survivingIds = new long[count];

            for (int i = 0; i < count; i++) {
                long docId = batchIds[i];
                // Note: residualMatcher still works row-by-row here,
                // but it's only called for indexed candidates.
                survivingIds[survivingCount++] = residualMatcher.matches(docId, ctx) ? docId : -1;

            }

            // 3. Late Materialization (The expensive part)
            // Only fetch the requested columns for rows that passed ALL filters
            for (long survivingId : survivingIds) {
                if (survivingId >= 0) {
                    var document = ctx.loadDocument(survivingId);
                    builder
                            .addDocument(new ResultDocument(document, 1.0f));
                }
            }
        }

        return builder.build();
    }

}
