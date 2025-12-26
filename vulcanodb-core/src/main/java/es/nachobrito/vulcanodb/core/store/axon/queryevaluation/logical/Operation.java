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

import es.nachobrito.vulcanodb.core.document.FieldValueType;
import es.nachobrito.vulcanodb.core.document.IntegerFieldValue;
import es.nachobrito.vulcanodb.core.document.StringFieldValue;
import es.nachobrito.vulcanodb.core.query.IntegerFieldOperator;
import es.nachobrito.vulcanodb.core.query.StringFieldOperator;

/**
 * @author nacho
 */
public enum Operation {
    INT_EQUALS(IntegerFieldValue.class),
    INT_LESS_THAN(IntegerFieldValue.class),
    INT_LESS_THAN_EQUAL(IntegerFieldValue.class),
    INT_GREATER_THAN(IntegerFieldValue.class),
    INT_GREATER_THAN_EQUAL(IntegerFieldValue.class),
    STRING_EQUALS(StringFieldValue.class),
    STRING_STARTS_WITH(StringFieldValue.class),
    STRING_ENDS_WITH(StringFieldValue.class),
    STRING_CONTAINS(StringFieldValue.class);

    private final Class<? extends FieldValueType<?>> operandType;

    Operation(Class<? extends FieldValueType<?>> operandType) {
        this.operandType = operandType;
    }

    public <T> Class<? extends FieldValueType<T>> getOperandType() {
        //noinspection unchecked
        return (Class<? extends FieldValueType<T>>) operandType;
    }

    public static Operation of(StringFieldOperator operator) {
        return switch (operator) {
            case EQUALS -> STRING_EQUALS;
            case STARTS_WITH -> STRING_STARTS_WITH;
            case ENDS_WITH -> STRING_ENDS_WITH;
            case CONTAINS -> STRING_CONTAINS;
        };
    }

    public static Operation of(IntegerFieldOperator operator) {
        return switch (operator) {
            case EQUALS -> INT_EQUALS;
            case LESS_THAN -> INT_LESS_THAN;
            case LESS_THAN_EQUAL -> INT_LESS_THAN_EQUAL;
            case GREATER_THAN -> INT_GREATER_THAN;
            case GREATER_THAN_EQUAL -> INT_GREATER_THAN_EQUAL;
        };
    }

    public void validateOperand(Object value) {
        if (
                (this.operandType.equals(StringFieldValue.class) && !(value instanceof String)) ||
                        (this.operandType.equals(IntegerFieldValue.class) && !(value instanceof Integer))

        ) {
            throw new IllegalArgumentException("Invalid type for operand %s: %s".formatted(this, value.getClass()));
        }
    }
}
