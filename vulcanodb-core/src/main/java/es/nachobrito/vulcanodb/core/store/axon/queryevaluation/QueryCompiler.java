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

import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.field.IndexedField;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.AndNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LeafNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LogicalNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.MatchAllNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.BitmapOperator;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical.DocumentMatcher;

/**
 * @author nacho
 */
public class QueryCompiler {


    // --- 1. Compile the Index Tree into a Bitmap Operator ---
    public BitmapOperator compileIndex(LogicalNode node) {
        if (node instanceof AndNode(LogicalNode left, LogicalNode right)) {
            BitmapOperator leftOp = compileIndex(left);
            BitmapOperator rightOp = compileIndex(right);

            // Return an executable lambda or class
            return ctx -> {
                DocIdSet leftSet = leftOp.compute(ctx);
                DocIdSet rightSet = rightOp.compute(ctx);
                leftSet.and(rightSet); // In-place optimization
                return leftSet;
            };
        } else if (node instanceof LeafNode leaf) {
            return ctx -> {
                IndexedField col = ctx.getIndexedField(leaf.fieldName());
                return col.getDocIds(leaf.value());
            };
        } else if (node instanceof MatchAllNode) {
            return ExecutionContext::getAllDocs;
        }

        throw new UnsupportedOperationException("Complex node in index tree: " + node);
    }

    // --- 2. Compile the Residual Tree into a Row Matcher ---

    public DocumentMatcher compileResidual(LogicalNode node) {
        return switch (node) {
            case AndNode andNode -> compileAndNode(andNode);
            case LeafNode leafNode -> compileLeafNode(leafNode);
            default -> throw new UnsupportedOperationException("Unknown node: " + node);
        };
    }

    private DocumentMatcher compileLeafNode(LeafNode leafNode) {
        return (docId, readers) -> {
            var col = readers.getScannableField(leafNode.fieldName(), leafNode.operator().getOperandType());
            Object val = col.getValue(docId);
            return leafNode.evaluate(val);
        };
    }

    private DocumentMatcher compileAndNode(AndNode andNode) {
        DocumentMatcher left = compileResidual(andNode.left());
        DocumentMatcher right = compileResidual(andNode.right());

        // Short-circuiting execution logic
        return (docId, readers) -> left.matches(docId, readers) && right.matches(docId, readers);
    }

}
