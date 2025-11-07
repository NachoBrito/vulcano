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

package es.nachobrito.vulcanodb.core.domain.model.query;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;

import java.util.ArrayList;
import java.util.List;

import static es.nachobrito.vulcanodb.core.domain.model.query.QueryOperator.AND;

/**
 * @author nacho
 */
public class MultiQuery implements Query {
    private final List<? extends Query> queries;
    private final QueryOperator operator;

    public MultiQuery(List<? extends Query> queries, QueryOperator operator) {
        this.queries = new ArrayList<>(queries);
        this.operator = operator;
    }

    @Override
    public Double apply(Document document) {
        var sum = 0.0;
        var partial = 0.0;
        var isAnd = operator.equals(AND);
        for (var query : queries) {
            partial = query.apply(document);
            if (isAnd && partial == 0.0) {
                return .0;
            }
            sum += partial;
        }
        return sum / queries.size();
    }
}
