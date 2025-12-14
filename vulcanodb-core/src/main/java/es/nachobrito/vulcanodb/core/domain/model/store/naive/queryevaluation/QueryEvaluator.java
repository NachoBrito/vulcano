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

package es.nachobrito.vulcanodb.core.domain.model.store.naive.queryevaluation;

import es.nachobrito.vulcanodb.core.domain.model.document.*;
import es.nachobrito.vulcanodb.core.domain.model.query.*;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.BiFunction;

import static es.nachobrito.vulcanodb.core.domain.model.query.QueryOperator.AND;

/**
 * @author nacho
 */
public class QueryEvaluator implements BiFunction<Query, Document, Float> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    public static final String FIELD_TYPE_WARNING = "Field {} in document {} is of invalid type '{}'.";
    public static final String FIELD_NOT_FOUND_WARNING = "Document {} does not contain a '{}' field";

    @Override
    public Float apply(Query query, Document document) {
        return switch (query) {
            case IntegerFieldQuery integerFieldQuery -> doApply(integerFieldQuery, document);
            case MultiQuery multiQuery -> doApply(multiQuery, document);
            case StringFieldQuery stringFieldQuery -> doApply(stringFieldQuery, document);
            case VectorFieldQuery vectorFieldQuery -> doApply(vectorFieldQuery, document);
        };

    }

    private Float doApply(VectorFieldQuery vectorFieldQuery, Document document) {
        var fieldName = vectorFieldQuery.getFieldName();
        var queryVector = vectorFieldQuery.getVector();

        var maybeField = document.field(fieldName);
        if (maybeField.isEmpty()) {
            log.warn(FIELD_NOT_FOUND_WARNING, document.id().value(), fieldName);
            return .0f;
        }

        var field = maybeField.get();

        Class<? extends FieldValueType<?>> type = field.type();
        if (type.equals(VectorFieldValue.class)) {
            //noinspection unchecked
            return handleVectorField((Field<float[], VectorFieldValue>) field, queryVector);
        }
        if (type.equals(MatrixFieldValue.class)) {
            //noinspection unchecked
            return handleMatrixField((Field<float[][], MatrixFieldValue>) field, queryVector);
        }

        log.warn(FIELD_TYPE_WARNING, fieldName, document.id().value(), field.type().getName());
        return .0f;
    }

    private Float handleMatrixField(Field<float[][], MatrixFieldValue> field, float[] queryVector) {
        var matrix = field.value();
        var sum = .0f;
        for (float[] floats : matrix) {
            sum += VectorSimilarity.getDefault().between(queryVector, floats);
        }
        return sum / matrix.length;
    }

    private Float handleVectorField(Field<float[], VectorFieldValue> field, float[] queryVector) {
        return VectorSimilarity.getDefault().between(queryVector, field.value());
    }

    private Float doApply(StringFieldQuery stringFieldQuery, Document document) {
        var fieldName = stringFieldQuery.getFieldName();
        var value = stringFieldQuery.getValue();
        var operator = stringFieldQuery.getOperator();

        var field = getField(fieldName, document, StringFieldValue.class);
        if (field.isEmpty()) {
            return 0.0f;
        }

        var stringField = field.get().value();
        return switch (operator) {
            case EQUALS -> stringField.equals(value) ? 1.0f : 0.0f;
            case STARTS_WITH -> stringField.startsWith(value) ? 1.0f : 0.0f;
            case ENDS_WITH -> stringField.endsWith(value) ? 1.0f : 0.0f;
            case CONTAINS -> stringField.contains(value) ? 1.0f : 0.0f;
        };
    }

    private Float doApply(MultiQuery multiQuery, Document document) {
        var operator = multiQuery.getOperator();
        var queries = multiQuery.getQueries();
        var sum = 0.0f;
        var partial = 0.0f;
        var isAnd = operator.equals(AND);
        for (var query : queries) {
            partial = this.apply(query, document);
            if (isAnd && partial == 0.0) {
                return .0f;
            }
            sum += partial;
        }
        return sum / queries.size();
    }

    private Float doApply(IntegerFieldQuery integerFieldQuery, Document document) {
        var fieldName = integerFieldQuery.getFieldName();
        var value = integerFieldQuery.getValue();
        var operator = integerFieldQuery.getOperator();

        var field = getField(fieldName, document, IntegerFieldValue.class);
        if (field.isEmpty()) {
            return 0.0f;
        }
        var integerField = field.get().value();
        return switch (operator) {
            case EQUALS -> integerField.equals(value) ? 1.0f : 0.0f;
            case LESS_THAN -> integerField < value ? 1.0f : 0.0f;
            case LESS_THAN_EQUAL -> integerField <= value ? 1.0f : 0.0f;
            case GREATER_THAN -> integerField > value ? 1.0f : 0.0f;
            case GREATER_THAN_EQUAL -> integerField >= value ? 1.0f : 0.0f;
        };
    }

    private <V, T extends FieldValueType<V>> Optional<Field<V, T>> getField(String fieldName, Document document, Class<T> expectedType) {
        var maybeField = document.field(fieldName);
        if (maybeField.isEmpty()) {
            log.warn(FIELD_NOT_FOUND_WARNING, document.id().value(), fieldName);
            return Optional.empty();
        }

        var field = maybeField.get();
        if (!(field.type().equals(expectedType))) {
            log.warn(FIELD_TYPE_WARNING, fieldName, document.id().value(), field.type().getName());
            return Optional.empty();
        }
        //noinspection unchecked
        return Optional.of((Field<V, T>) field);
    }
}
