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

import java.util.Objects;

/**
 * @author nacho
 */
public final class StringFieldQuery implements Query {
    private final String value;
    private final String fieldName;
    private final StringFieldOperator operator;

    public StringFieldQuery(String value, String fieldName, StringFieldOperator operator) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(operator);
        this.value = value;
        this.fieldName = fieldName;
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public String getFieldName() {
        return fieldName;
    }

    public StringFieldOperator getOperator() {
        return operator;
    }
}
