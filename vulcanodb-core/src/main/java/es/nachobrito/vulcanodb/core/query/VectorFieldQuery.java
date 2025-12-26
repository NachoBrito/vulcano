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

import java.util.Arrays;
import java.util.Objects;

/**
 * @author nacho
 */
public final class VectorFieldQuery implements Query {
    private final float[] vector;
    private final String fieldName;


    public VectorFieldQuery(float[] vector, String fieldName) {
        Objects.requireNonNull(vector);
        Objects.requireNonNull(fieldName);
        this.vector = Arrays.copyOf(vector, vector.length);
        this.fieldName = fieldName;
    }

    public float[] getVector() {
        return Arrays.copyOf(vector, vector.length);
    }

    public String getFieldName() {
        return fieldName;
    }
}
