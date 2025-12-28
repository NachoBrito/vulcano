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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical;

import es.nachobrito.vulcanodb.core.query.*;

/**
 * @author nacho
 */
// Basic node types for our AST
public sealed interface LogicalNode permits AndNode, OrNode, NotNode, LeafNode, MatchAllNode, MatchNoneNode {


    /**
     * Builds a tree representation of the provided query
     *
     * @param query the query
     * @return the root node of the tree representation
     */
    static LogicalNode of(Query query) {
        return switch (query) {
            case MultiQuery multiQuery -> ofMultiQuery(multiQuery);
            case IntegerFieldQuery integerFieldQuery -> ofIntegerQuery(integerFieldQuery);
            case StringFieldQuery stringFieldQuery -> ofStringQuery(stringFieldQuery);
            case VectorFieldQuery vectorFieldQuery -> ofVectorQuery(vectorFieldQuery);
            case NegativeQuery negativeQuery -> ofNegativeQuery(negativeQuery);
        };
    }

    static LogicalNode ofNegativeQuery(NegativeQuery negativeQuery) {
        return new NotNode(of(negativeQuery.query()));
    }

    static LogicalNode ofVectorQuery(VectorFieldQuery vectorFieldQuery) {
        return new LeafNode(vectorFieldQuery.fieldName(), Operation.VECTOR_SIMILAR, vectorFieldQuery.vector());
    }

    static LogicalNode ofStringQuery(StringFieldQuery stringFieldQuery) {
        return new LeafNode(stringFieldQuery.fieldName(), Operation.of(stringFieldQuery.operator()), stringFieldQuery.value());
    }

    static LogicalNode ofIntegerQuery(IntegerFieldQuery integerFieldQuery) {
        return new LeafNode(integerFieldQuery.fieldName(), Operation.of(integerFieldQuery.operator()), integerFieldQuery.value());
    }

    static LogicalNode ofMultiQuery(MultiQuery multiQuery) {
        if (multiQuery.queries().size() < 2) {
            throw new IllegalArgumentException("Logical operations require at least two operators");
        }
        return switch (multiQuery.operator()) {
            case AND -> andNodeOf(multiQuery);
            case OR -> orNodeOf(multiQuery);
        };
    }

    static OrNode orNodeOf(MultiQuery multiQuery) {
        var queries = multiQuery.queries();
        var node = new OrNode(of(queries.get(0)), of(queries.get(1)));
        if (queries.size() > 2) {
            for (int i = 1; i < queries.size(); i++) {
                node = new OrNode(node, of(queries.get(i)));
            }
        }
        return node;
    }

    static AndNode andNodeOf(MultiQuery multiQuery) {
        var queries = multiQuery.queries();
        var node = new AndNode(of(queries.get(0)), of(queries.get(1)));
        if (queries.size() > 2) {
            for (int i = 1; i < queries.size(); i++) {
                node = new AndNode(node, of(queries.get(i)));
            }
        }
        return node;
    }
}