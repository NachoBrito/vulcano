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
    private final List<VectorQuery> vectorQueries = new ArrayList<>();

    QueryBuilder() {

    }


    ///  Match documents where the value in the provided field is similar (semantic search) to the vector provided.
    ///
    /// @param vector    the vector to search
    /// @param fieldName the field to compare
    /// @return this query builder
    public QueryBuilder isSimilarTo(double[] vector, String fieldName) {
        return allSimilarTo(vector, List.of(fieldName));
    }

    /// Match documents where the value of **all** the fields in the list are similar the vector provided.
    ///
    /// @param vector     the vector to search
    /// @param fieldNames the field names to compare
    /// @return this query builder
    public QueryBuilder allSimilarTo(double[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, AND, new CosineSimilarity());
    }

    /// Match documents where the value of **any** of the fields in the list is similar to the vector provided.
    ///
    /// @param vector     the vector to search
    /// @param fieldNames the field names to compare
    /// @return this query builder
    public QueryBuilder anySimilarTo(double[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, OR, new CosineSimilarity());
    }

    private QueryBuilder addVectorQuery(double[] vector, List<String> fieldNames, Operator operator, VectorSimilarity vectorSimilarity) {

        var fieldQueries = fieldNames
                .stream()
                .map(it -> new VectorFieldQuery(vector, it, vectorSimilarity))
                .toList();
        vectorQueries.add(new VectorQuery(fieldQueries, operator));
        return this;
    }

    ///  Set the operator for this query, affecting all the conditions added with [#isSimilarTo(double\[\], String)],
    /// [#allSimilarTo(double\[\], List)], [#anySimilarTo(double\[\], List)]
    ///
    /// @param operator [Operator]
    /// @return this query builder
    public QueryBuilder withOperator(Operator operator) {
        this.operator = operator;
        return this;
    }


    public Query build() {
        return new MultiVectorQuery(vectorQueries, operator);
    }
}
