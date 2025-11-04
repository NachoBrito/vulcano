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

import es.nachobrito.vulcanodb.core.domain.model.query.similarity.CosineSimilarity;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;

import java.util.ArrayList;
import java.util.List;

import static es.nachobrito.vulcanodb.core.domain.model.query.Operator.AND;
import static es.nachobrito.vulcanodb.core.domain.model.query.Operator.OR;

/**
 * @author nacho
 */
public class QueryBuilder {
    private Operator operator = AND;
    private VectorSimilarity vectorSimilarity = new CosineSimilarity();
    private List<VectorQuery> vectorQueries = new ArrayList<>();

    QueryBuilder() {

    }

    public QueryBuilder and(double[] vector, String fieldName) {
        return and(vector, List.of(fieldName));
    }

    public QueryBuilder or(double[] vector, String fieldName) {

        return or(vector, List.of(fieldName));
    }

    public QueryBuilder and(double[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, AND);
    }

    public QueryBuilder or(double[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, OR);
    }

    private QueryBuilder addVectorQuery(double[] vector, List<String> fieldNames, Operator operator) {
        var fieldQueries = fieldNames
                .stream()
                .map(it -> new VectorFieldQuery(vector, it, vectorSimilarity))
                .toList();
        vectorQueries.add(new VectorQuery(fieldQueries, operator));
        return this;
    }

    public QueryBuilder withOperator(Operator operator) {
        this.operator = operator;
        return this;
    }

    public QueryBuilder withVectorSimilarity(VectorSimilarity vectorSimilarity) {
        this.vectorSimilarity = vectorSimilarity;
        return this;
    }

    public Query build() {
        return new MultiVectorQuery(vectorQueries, operator);
    }
}
