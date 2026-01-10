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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * @author nacho
 */
public class VectorizedRunner {
    private static final int BATCH_SIZE = 1024; // Fits well in L1/L2 cache

    private record ScoredDoc(long id, float score) {
    }

    public QueryResult run(
            long[] candidates,
            DocumentMatcher residualMatcher,
            ExecutionContext ctx,
            int maxResults) {
        var builder = QueryResult.builder();

        long[] batchIds = new long[BATCH_SIZE];
        int count = 0;

        // Min-heap to keep the top K results (worst of the top at the head)
        PriorityQueue<ScoredDoc> topK = new PriorityQueue<>(Comparator.comparingDouble(ScoredDoc::score));

        /*
        Go over all the candidates, evaluate the residual matcher for every one so they can be sorted by score to
        take the top <maxResult>
         */
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
                    var match = residualMatcher.matches(docId, ctx);
                    if (match.matches()) {
                        ctx.saveVectorScore(docId, match.score());

                        if (topK.size() < maxResults) {
                            topK.add(new ScoredDoc(docId, ctx.getAverageScore(docId)));
                        } else if (match.score() > topK.peek().score()) {
                            topK.poll();
                            topK.add(new ScoredDoc(docId, ctx.getAverageScore(docId)));
                        }
                    }
                }
                count = 0;
            }
        }

        if (!topK.isEmpty()) {
            var sortedResults = new ArrayList<ScoredDoc>(topK.size());
            while (!topK.isEmpty()) {
                sortedResults.add(topK.poll());
            }
            // PriorityQueue is a min-heap, so poll() gives smallest first. We want highest score first.
            for (int i = sortedResults.size() - 1; i >= 0; i--) {
                var entry = sortedResults.get(i);
                var document = ctx.loadDocument(entry.id())
                        .orElseThrow(
                                () -> new AxonDataStoreException("Could not load document with internal id " + entry.id()));
                builder.addDocument(new ResultDocument(document, entry.score()));
            }
        }


        return builder.build();
    }

}
