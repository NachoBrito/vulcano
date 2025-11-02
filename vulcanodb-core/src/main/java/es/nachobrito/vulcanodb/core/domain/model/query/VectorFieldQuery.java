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
import es.nachobrito.vulcanodb.core.domain.model.document.DoubleVectorFieldValue;
import es.nachobrito.vulcanodb.core.domain.model.document.Field;
import es.nachobrito.vulcanodb.core.domain.model.query.similarity.VectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author nacho
 */
public class VectorFieldQuery implements Query {
    private static final Logger log = LoggerFactory.getLogger(VectorFieldQuery.class);

    private final double[] vector;
    private final String fieldName;
    private final VectorSimilarity vectorSimilarity;

    public VectorFieldQuery(double[] vector, String fieldName, VectorSimilarity vectorSimilarity) {
        this.vector = vector;
        this.fieldName = fieldName;
        this.vectorSimilarity = vectorSimilarity;
    }

    @Override
    public Double apply(Document document) {
        var maybeField = document.field(fieldName);
        if (maybeField.isEmpty()) {
            log.warn("Document {} does not contain a '{}' field", document.id().value(), fieldName);
            return .0;
        }
        var field = maybeField.get();
        if (!(field.type().equals(DoubleVectorFieldValue.class))) {
            log.warn("Field {} in document {} is not a vector field ({})", fieldName, document.id().value(), field.type().getName());
            return .0;
        }

        @SuppressWarnings("unchecked") var vectorField = (Field<DoubleVectorFieldValue>) field;
        return vectorSimilarity.between(this.vector, vectorField.value().value());
    }
}
