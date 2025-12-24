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

package es.nachobrito.vulcanodb.core.domain.model.document;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author nacho
 */
public record VectorFieldValue(float[] value) implements FieldValueType<float[]> {
    public VectorFieldValue {
        value = Arrays.copyOf(value, value.length);
    }

    @Override
    public float[] value() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        VectorFieldValue that = (VectorFieldValue) o;
        return Objects.deepEquals(value(), that.value());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value());
    }
}
