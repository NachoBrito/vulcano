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

import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.*;

/**
 * @author nacho
 */
public class QuerySplitter {
    private final IndexRegistry indexRegistry;

    public QuerySplitter(IndexRegistry indexRegistry) {
        this.indexRegistry = indexRegistry;
    }

    /**
     * @param indexTree    Evaluate this FIRST (Bitmaps)
     * @param residualTree Evaluate this SECOND (Scan)
     */
    public record SplitResult(LogicalNode indexTree, LogicalNode residualTree) {
    }

    /**
     * Main entry point.
     */
    public SplitResult split(LogicalNode node) {
        if (node instanceof LeafNode) {
            return splitLeaf((LeafNode<?>) node);
        } else if (node instanceof AndNode) {
            return splitAnd((AndNode) node);
        } else if (node instanceof OrNode) {
            return splitOr((OrNode) node);
        } else if (node instanceof NotNode) {
            return splitNot((NotNode) node);
        } else if (node instanceof MatchAllNode || node instanceof MatchNoneNode) {
            // These are pure logic nodes, treated as purely indexable (cost is 0)
            return new SplitResult(node, MatchAllNode.INSTANCE);
        }
        throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }

    // --- Handling Leaf Nodes ---

    private SplitResult splitLeaf(LeafNode<?> node) {
        if (indexRegistry.isIndexed(node.fieldName())) {
            // If indexed, it goes entirely to the Index Tree.
            // The Residual Tree gets "MatchAll" (meaning: no further work needed for this node).
            return new SplitResult(node, MatchAllNode.INSTANCE);
        } else {
            // If not indexed, Index Tree gets "MatchAll" (meaning: select everything).
            // The actual work happens in Residual Tree.
            return new SplitResult(MatchAllNode.INSTANCE, node);
        }
    }

    // --- Handling AND (The Logic Intersection) ---

    private SplitResult splitAnd(AndNode node) {
        SplitResult left = split(node.left());
        SplitResult right = split(node.right());

        // Combine the Index parts
        LogicalNode newIndex = simplifyAnd(left.indexTree, right.indexTree);

        // Combine the Residual parts
        LogicalNode newResidual = simplifyAnd(left.residualTree, right.residualTree);

        return new SplitResult(newIndex, newResidual);
    }

    // --- Handling OR (The "Weakest Link" Rule) ---

    private SplitResult splitOr(OrNode node) {
        SplitResult left = split(node.left());
        SplitResult right = split(node.right());

        // CRITICAL LOGIC:
        // We can only use the Index for an OR if BOTH sides are fully indexable.
        // If "A is indexed" OR "B is not indexed", we cannot filter out ANYTHING using A,
        // because we still need to fetch rows for B.

        boolean isLeftFullyIndexed = isMatchAll(left.residualTree);
        boolean isRightFullyIndexed = isMatchAll(right.residualTree);

        if (isLeftFullyIndexed && isRightFullyIndexed) {
            // Best Case: Both are indexed. Combine index trees, no residual work.
            return new SplitResult(
                    new OrNode(left.indexTree, right.indexTree),
                    MatchAllNode.INSTANCE
            );
        } else {
            // Fallback: If ANY part requires a scan, the WHOLE OR block must be scanned.
            // We cannot easily merge a Bitmap with a Scan Iterator.
            // The Index Tree becomes "MatchAll" (select everything)
            // The Residual Tree is the original OR node.
            return new SplitResult(MatchAllNode.INSTANCE, node);
        }
    }

    // --- Handling NOT ---

    private SplitResult splitNot(NotNode node) {
        SplitResult child = split(node.child());

        // If the child was fully indexed, we can just invert the index tree.
        if (isMatchAll(child.residualTree)) {
            return new SplitResult(new NotNode(child.indexTree), MatchAllNode.INSTANCE);
        }

        // If the child had ANY residual work, we generally cannot invert safely 
        // without scanning everything.
        // Example: NOT (Unindexed_Col == 5). We must scan all rows to find != 5.
        return new SplitResult(MatchAllNode.INSTANCE, node);
    }

    // --- Simplification Helpers (Optimizer) ---

    private LogicalNode simplifyAnd(LogicalNode left, LogicalNode right) {
        if (isMatchAll(left)) return right;
        if (isMatchAll(right)) return left;
        if (isMatchNone(left) || isMatchNone(right)) return MatchNoneNode.INSTANCE;
        return new AndNode(left, right);
    }

    private boolean isMatchAll(LogicalNode node) {
        return node instanceof MatchAllNode;
    }

    private boolean isMatchNone(LogicalNode node) {
        return node instanceof MatchNoneNode;
    }
}
