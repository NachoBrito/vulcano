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
import es.nachobrito.vulcanodb.core.domain.model.document.Field;
import es.nachobrito.vulcanodb.core.domain.model.document.IntegerFieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author nacho
 */
public class IntegerFieldQuery implements Query {
    private static final Logger log = LoggerFactory.getLogger(IntegerFieldQuery.class);
    public static final String FIELD_TYPE_WARNING = "Field {} in document {} is of type '{}'. You can only search integer fields.";
    public static final String FIELD_NOT_FOUND_WARNING = "Document {} does not contain a '{}' field";

    private final Integer value;
    private final String fieldName;
    private final IntegerFieldOperator operator;

    public IntegerFieldQuery(Integer value, String fieldName, IntegerFieldOperator operator) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(operator);
        this.value = value;
        this.fieldName = fieldName;
        this.operator = operator;
    }


    @Override
    public Double apply(Document document) {
        var maybeField = document.field(fieldName);
        if (maybeField.isEmpty()) {
            log.warn(FIELD_NOT_FOUND_WARNING, document.id().value(), fieldName);
            return .0;
        }

        var field = maybeField.get();
        if (!(field.type().equals(IntegerFieldValue.class))) {
            log.warn(FIELD_TYPE_WARNING, fieldName, document.id().value(), field.type().getName());
            return .0;
        }

        @SuppressWarnings("unchecked") var integerField = ((Field<Integer, IntegerFieldValue>) field).value();
        return switch (operator) {
            case EQUALS -> integerField.equals(value) ? 1.0 : 0.0;
            case LESS_THAN -> integerField < value ? 1.0 : 0.0;
            case LESS_THAN_EQUAL -> integerField <= value ? 1.0 : 0.0;
            case GREATER_THAN -> integerField > value ? 1.0 : 0.0;
            case GREATER_THAN_EQUAL -> integerField >= value ? 1.0 : 0.0;
        };
    }
}
