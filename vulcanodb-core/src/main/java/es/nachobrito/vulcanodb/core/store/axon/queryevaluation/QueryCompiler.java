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
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.*;
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
        } else if (node instanceof LeafNode<?> leaf) {
            return ctx -> compileLeafIndex(ctx, leaf);
        } else if (node instanceof MatchAllNode) {
            return ExecutionContext::getAllDocs;
        }

        throw new UnsupportedOperationException("Complex node in index tree: " + node);
    }

    @SuppressWarnings("unchecked")
    private <V> DocIdSet compileLeafIndex(ExecutionContext ctx, LeafNode<V> leaf) {
        IndexedField<V> col = ctx.getIndexedField(leaf);
        return col.getDocIds(leaf);
    }

    // --- 2. Compile the Residual Tree into a Row Matcher ---

    public DocumentMatcher compileResidual(LogicalNode node) {
        return switch (node) {
            case AndNode andNode -> compileAndNode(andNode);
            case OrNode orNode -> compileOrNode(orNode);
            case LeafNode<?> leafNode -> compileLeafNode(leafNode);
            case NotNode notNode -> compileNotNode(notNode);
            case MatchAllNode _ -> compileMatchAllNode();
            case MatchNoneNode _ -> compileMatchNoneNode();
        };
    }

    private DocumentMatcher compileMatchNoneNode() {
        return (docId, readers) -> DocumentMatcher.Score.of(false);
    }

    private DocumentMatcher compileMatchAllNode() {
        return (docId, readers) -> DocumentMatcher.Score.of(true);
    }

    private DocumentMatcher compileNotNode(NotNode notNode) {
        var negated = compileResidual(notNode.child());
        return (docId, readers) -> negated.matches(docId, readers).negate();
    }

    private <V> DocumentMatcher compileLeafNode(LeafNode<V> leafNode) {
        return (docId, readers) -> {
            var col = readers.getScannableField(leafNode.fieldName(), leafNode.operator().getOperandType());
            @SuppressWarnings("unchecked")
            V val = (V) col.getValue(docId);
            return DocumentMatcher.Score.of(leafNode.evaluate(val));
        };
    }

    private DocumentMatcher compileAndNode(AndNode andNode) {
        DocumentMatcher left = compileResidual(andNode.left());
        DocumentMatcher right = compileResidual(andNode.right());

        // Short-circuiting execution logic
        return (docId, readers) -> left.matches(docId, readers).and(right.matches(docId, readers));
    }

    private DocumentMatcher compileOrNode(OrNode orNode) {
        DocumentMatcher left = compileResidual(orNode.left());
        DocumentMatcher right = compileResidual(orNode.right());

        // Short-circuiting execution logic
        return (docId, readers) -> left.matches(docId, readers).or(right.matches(docId, readers));
    }

}
