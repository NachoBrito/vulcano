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
 * A builder class for creating database queries with a fluent API.
 * Supports vector similarity searches, string matches, and integer comparisons.
 *
 * @author nacho
 */
public class QueryBuilder {
    private QueryOperator operator = AND;
    private final PriorityQueue<Query> queries = new PriorityQueue<>(new QueryComparator());

    public QueryBuilder() {

    }


    /**
     * Matches documents where the specified field is semantically similar to the given vector.
     *
     * @param vector    the query vector
     * @param fieldName the name of the field to compare
     * @return this builder instance
     */
    public QueryBuilder isSimilarTo(float[] vector, String fieldName) {
        return allSimilarTo(vector, List.of(fieldName));
    }

    /**
     * Matches documents where all specified fields are semantically similar to the given vector.
     *
     * @param vector     the query vector
     * @param fieldNames the names of the fields to compare
     * @return this builder instance
     */
    public QueryBuilder allSimilarTo(float[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, AND, new CosineSimilarity());
    }

    /**
     * Matches documents where any of the specified fields are semantically similar to the given vector.
     *
     * @param vector     the query vector
     * @param fieldNames the names of the fields to compare
     * @return this builder instance
     */
    public QueryBuilder anySimilarTo(float[] vector, List<String> fieldNames) {
        return addVectorQuery(vector, fieldNames, OR, new CosineSimilarity());
    }

    /**
     * Matches documents where the specified string field starts with the given prefix.
     *
     * @param prefix    the prefix to match
     * @param fieldName the name of the string field
     * @return this builder instance
     */
    public QueryBuilder startsWith(String prefix, String fieldName) {
        queries.add(new StringFieldQuery(prefix, fieldName, StringFieldOperator.STARTS_WITH));
        return this;
    }

    /**
     * Matches documents where the specified string field ends with the given suffix.
     *
     * @param suffix    the suffix to match
     * @param fieldName the name of the string field
     * @return this builder instance
     */
    public QueryBuilder endsWith(String suffix, String fieldName) {
        queries.add(new StringFieldQuery(suffix, fieldName, StringFieldOperator.ENDS_WITH));
        return this;
    }

    /**
     * Matches documents where the specified string field contains the given value.
     *
     * @param value     the substring to match
     * @param fieldName the name of the string field
     * @return this builder instance
     */
    public QueryBuilder contains(String value, String fieldName) {
        queries.add(new StringFieldQuery(value, fieldName, StringFieldOperator.CONTAINS));
        return this;
    }

    /**
     * Matches documents where the specified string field is exactly equal to the given value.
     *
     * @param value     the string to match
     * @param fieldName the name of the string field
     * @return this builder instance
     */
    public QueryBuilder isEqual(String value, String fieldName) {
        queries.add(new StringFieldQuery(value, fieldName, StringFieldOperator.EQUALS));
        return this;
    }

    /**
     * Matches documents where the specified integer field is equal to the given value.
     *
     * @param value     the value to compare
     * @param fieldName the name of the integer field
     * @return this builder instance
     */
    public QueryBuilder isEqual(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.EQUALS));
        return this;
    }

    /**
     * Matches documents where the specified integer field is less than the given value.
     *
     * @param value     the value to compare
     * @param fieldName the name of the integer field
     * @return this builder instance
     */
    public QueryBuilder isLessThan(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.LESS_THAN));
        return this;
    }

    /**
     * Matches documents where the specified integer field is less than or equal to the given value.
     *
     * @param value     the value to compare
     * @param fieldName the name of the integer field
     * @return this builder instance
     */
    public QueryBuilder isLessThanOrEqual(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.LESS_THAN_EQUAL));
        return this;
    }

    /**
     * Matches documents where the specified integer field is greater than the given value.
     *
     * @param value     the value to compare
     * @param fieldName the name of the integer field
     * @return this builder instance
     */
    public QueryBuilder isGreaterThan(Integer value, String fieldName) {
        queries.add(new IntegerFieldQuery(value, fieldName, IntegerFieldOperator.GREATER_THAN));
        return this;
    }

    /**
     * Matches documents where the specified integer field is greater than or equal to the given value.
     *
     * @param value     the value to compare
     * @param fieldName the name of the integer field
     * @return this builder instance
     */
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

    /**
     * Sets the logical operator used to combine query conditions.
     *
     * @param operator the logical operator (AND/OR)
     * @return this builder instance
     */
    public QueryBuilder withOperator(QueryOperator operator) {
        this.operator = operator;
        return this;
    }


    /**
     * Negates the query criteria provided by another builder.
     *
     * @param other the other query builder
     * @return this builder instance
     */
    public QueryBuilder not(QueryBuilder other) {
        return this.not(other.build());
    }

    /**
     * Negates the specified query criteria.
     *
     * @param other the query to negate
     * @return this builder instance
     */
    public QueryBuilder not(Query other) {
        queries.add(new NegativeQuery(other));
        return this;
    }


    /**
     * Builds the final {@link Query} instance based on the configured conditions.
     *
     * @return the constructed query
     * @throws IllegalStateException if no query conditions have been added
     */
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
