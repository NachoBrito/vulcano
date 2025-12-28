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

package es.nachobrito.vulcanodb.core.query;

import es.nachobrito.vulcanodb.core.query.similarity.CosineSimilarity;
import es.nachobrito.vulcanodb.core.query.similarity.VectorSimilarity;

import java.util.List;
import java.util.PriorityQueue;

import static es.nachobrito.vulcanodb.core.query.QueryOperator.AND;
import static es.nachobrito.vulcanodb.core.query.QueryOperator.OR;

/**
 * @author nacho
 */
public class QueryBuilder {
    private QueryOperator operator = AND;
    private final PriorityQueue<Query> queries = new PriorityQueue<>(new QueryComparator());

    QueryBuilder() {

    }


    ///  Match documents where the value in the provided field is similar (semantic search) to the vector provided.
    ///
    /// @param vector    the vector to search
    /// @param fieldName the field to compare
    /// @return this query builder
    public QueryBuilder isSimilarTo(float[] vector, String fieldName) {
        return allSimilarTo(vector, List.of(fieldName));
    }

    /// Match documents where the value of **all** the fields in the list are similar the vector provided.
    ///
    /// @param vector     the vector to search
    /// @param fieldNames the field names to compare
    /// @return this query builder
    public QueryBuilder allSimilarTo(float[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, AND, new CosineSimilarity());
    }

    /// Match documents where the value of **any** of the fields in the list is similar to the vector provided.
    ///
    /// @param vector     the vector to search
    /// @param fieldNames the field names to compare
    /// @return this query builder
    public QueryBuilder anySimilarTo(float[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, OR, new CosineSimilarity());
    }

    /// Match documents where the value of the *fieldMame* field starts with the provided prefix
    ///
    /// @param prefix    the prefix to search
    /// @param fieldName the field to search in
    /// @return this query builder
    public QueryBuilder startsWith(String prefix, String fieldName) {
        queries.add(new StringFieldQuery(prefix, fieldName, StringFieldOperator.STARTS_WITH));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field ends with the provided suffix
    ///
    /// @param suffix    the suffix to search
    /// @param fieldName the field to search in
    /// @return this query builder
    public QueryBuilder endsWith(String suffix, String fieldName) {
        queries.add(new StringFieldQuery(suffix, fieldName, StringFieldOperator.ENDS_WITH));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field contains the provided value
    ///
    /// @param value     the text to search
    /// @param fieldName the field to search in
    /// @return this query builder
    public QueryBuilder contains(String value, String fieldName) {
        queries.add(new StringFieldQuery(value, fieldName, StringFieldOperator.CONTAINS));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is equal to the provided value
    ///
    /// @param value     the text to search
    /// @param fieldName the field to search in
    /// @return this query builder
    public QueryBuilder isEqual(String value, String fieldName) {
        queries.add(new StringFieldQuery(value, fieldName, StringFieldOperator.EQUALS));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is equal to the provided value
    ///
    /// @param value     the integer to compare
    /// @param fieldName the field to compare with
    /// @return this query builder
    public QueryBuilder isEqual(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.EQUALS));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is less than the provided value
    ///
    /// @param value     the integer to compare
    /// @param fieldName the field to compare with
    /// @return this query builder
    public QueryBuilder isLessThan(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.LESS_THAN));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is less than or equal the provided value
    ///
    /// @param value     the integer to compare
    /// @param fieldName the field to compare with
    /// @return this query builder
    public QueryBuilder isLessThanOrEqual(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.LESS_THAN_EQUAL));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is greater than the provided value
    ///
    /// @param value     the integer to compare
    /// @param fieldName the field to compare with
    /// @return this query builder
    public QueryBuilder isGreaterThan(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.GREATER_THAN));
        return this;
    }

    /// Match documents where the value of the *fieldMame* field is greater than or equal the provided value
    ///
    /// @param value     the integer to compare
    /// @param fieldName the field to compare with
    /// @return this query builder
    public QueryBuilder isGreaterThanOrEqual(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.GREATER_THAN_EQUAL));
        return this;
    }

    private QueryBuilder addVectorQuery(float[] vector, List<String> fieldNames, QueryOperator operator, VectorSimilarity vectorSimilarity) {

        var fieldQueries = fieldNames
                .stream()
                .map(it -> new VectorFieldQuery(vector, it))
                .toList();
        if (fieldQueries.size() == 1) {
            queries.add(fieldQueries.getFirst());
            return this;
        }

        queries.add(new MultiQuery(fieldQueries, operator));
        return this;
    }

    ///  Set the operator for this query, affecting all the conditions added with [#isSimilarTo(float\[\], String)],
    /// [#allSimilarTo(float\[\], List)], [#anySimilarTo(float\[\], List)]
    ///
    /// @param operator [QueryOperator]
    /// @return this query builder
    public QueryBuilder withOperator(QueryOperator operator) {
        this.operator = operator;
        return this;
    }


    /// Match queries that do not match the query generated by the other builder
    ///
    /// @param other the builder producing the query to negate
    /// @return this query builder
    public QueryBuilder not(QueryBuilder other) {
        return this.not(other.build());
    }

    /// Match queries that do not match the other query
    ///
    /// @param other the query to negate
    /// @return this query builder
    public QueryBuilder not(Query other) {
        queries.add(new NegativeQuery(other));
        return this;
    }


    public Query build() {
        if (queries.isEmpty()) {
            throw new IllegalStateException("Query cannot be empty");
        }
        if (queries.size() == 1) {
            return queries.peek();
        }
        return new MultiQuery(queries.stream().toList(), operator);
    }
}
