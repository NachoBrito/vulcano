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

import es.nachobrito.vulcanodb.core.domain.model.document.*;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author nacho
 */
public class VectorFieldQuery implements Query {
    private static final Logger log = LoggerFactory.getLogger(VectorFieldQuery.class);
    public static final String FIELD_TYPE_WARNING = "Field {} in document {} is of type '{}'. You can only search vector or matrix fields.";
    public static final String FIELD_NOT_FOUND_WARNING = "Document {} does not contain a '{}' field";
    private final float[] vector;
    private final String fieldName;
    private final VectorSimilarity vectorSimilarity;


    public VectorFieldQuery(float[] vector, String fieldName, VectorSimilarity vectorSimilarity) {
        Objects.requireNonNull(vector);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(vectorSimilarity);
        this.vector = Arrays.copyOf(vector, vector.length);
        this.fieldName = fieldName;
        this.vectorSimilarity = vectorSimilarity;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Float apply(Document document) {
        var maybeField = document.field(fieldName);
        if (maybeField.isEmpty()) {
            log.warn(FIELD_NOT_FOUND_WARNING, document.id().value(), fieldName);
            return .0f;
        }

        var field = maybeField.get();

        Class<? extends FieldValueType<?>> type = field.type();
        if (type.equals(VectorFieldValue.class)) {
            return handleVectorField((Field<float[], VectorFieldValue>) field);
        }
        if (type.equals(MatrixFieldValue.class)) {
            return handleMatrixField((Field<float[][], MatrixFieldValue>) field);
        }

        log.warn(FIELD_TYPE_WARNING, fieldName, document.id().value(), field.type().getName());
        return .0f;

    }

    private Float handleMatrixField(Field<float[][], MatrixFieldValue> field) {
        var matrix = field.value();
        var sum = .0f;
        for (float[] floats : matrix) {
            sum += vectorSimilarity.between(this.vector, floats);
        }
        return sum / matrix.length;
    }

    private Float handleVectorField(Field<float[], VectorFieldValue> field) {
        return vectorSimilarity.between(this.vector, field.value());
    }
}
