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
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LogicalNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.BitmapOperator;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.DocumentMatcher;

/**
 * @author nacho
 */
public class QueryExecutor {
    private final QuerySplitter splitter;
    private final QueryCompiler compiler;
    private final ExecutionContext context;
    private final VectorizedRunner vectorizedRunner;

    public QueryExecutor(ExecutionContext context, IndexRegistry indexRegistry) {
        this.splitter = new QuerySplitter(indexRegistry);
        this.compiler = new QueryCompiler();
        this.context = context;
        this.vectorizedRunner = new VectorizedRunner();
    }

    public QueryResult execute(LogicalNode rawQuery, int maxResults) {

        // Phase 1: Optimization (Logical)
        // Split query into "Indexable" vs "Residual"
        var splitResult = splitter.split(rawQuery);

        // Phase 2: Compilation (Physical)
        // Convert logic records into executable code
        BitmapOperator indexOp = compiler.compileIndex(splitResult.indexTree());
        DocumentMatcher residualOp = compiler.compileResidual(splitResult.residualTree());

        // Phase 3: Index Execution (Fast Set Math)
        // Fetch candidate DocIDs using RoaringBitmaps
        var candidates = indexOp
                .compute(context)
                .stream()
                .toArray();

        // Phase 4: Vectorized Scan (Slow IO)
        // Pass the candidates and the residual matcher to the runner
        return vectorizedRunner.run(candidates, residualOp, context, maxResults);
    }
}
