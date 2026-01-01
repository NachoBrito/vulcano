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
import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.DocumentMatcher;

import java.util.Arrays;

/**
 * @author nacho
 */
public class VectorizedRunner {
    private static final int BATCH_SIZE = 1024; // Fits well in L1/L2 cache

    public QueryResult run(
            long[] candidates,
            DocumentMatcher residualMatcher,
            ExecutionContext ctx,
            int maxResults) {
        var builder = QueryResult.builder();

        long[] batchIds = new long[BATCH_SIZE];
        int count = 0;
        int survivingCount = 0;
        long[] survivingIds = new long[Math.min(maxResults, candidates.length)];
        int lastIndex = candidates.length - 1;
        for (int candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
            long candidateId = candidates[candidateIndex];
            batchIds[count++] = candidateId;
            if (count == BATCH_SIZE || candidateIndex == lastIndex) {
                //PROCESS BATCH:
                // Apply Residual Filtering
                // We only keep IDs that pass the scan-based predicates
                for (int i = 0; i < count; i++) {
                    long docId = batchIds[i];
                    // Note: residualMatcher still works row-by-row here,
                    // but it's only called for indexed candidates.
                    if (residualMatcher.matches(docId, ctx)) {
                        ctx.saveVectorScore(docId, 1.0f);
                        survivingIds[survivingCount++] = docId;
                        if (survivingCount == survivingIds.length) {
                            break;
                        }
                    }
                }
                count = 0;
            }
            if (survivingCount == survivingIds.length) {
                break;
            }
        }

        if (survivingCount > 0) {
            Arrays.stream(survivingIds)
                    .limit(survivingCount)
                    .mapToObj(id -> {
                        var score = ctx.getAverageScore(id);
                        var document = ctx.loadDocument(id)
                                .orElseThrow(
                                        () -> new AxonDataStoreException("Could not load document with internal id " + id));
                        return new ResultDocument(document, score);
                    })
                    .sorted()
                    .forEach(builder::addDocument);
        }


        return builder.build();
    }

}
