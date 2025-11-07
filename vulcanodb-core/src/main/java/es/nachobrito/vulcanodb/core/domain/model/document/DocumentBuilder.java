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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nacho
 */
public class DocumentBuilder {
    private final List<Field<?, ?>> fields = new ArrayList<>();

    DocumentBuilder() {

    }

    /// Adds a new vector field to the document
    ///
    /// @param name  the field name
    /// @param value the field value
    /// @return this builder
    public DocumentBuilder withVectorField(String name, double[] value) {
        this.fields.add(new Field<>(name, DoubleVectorFieldValue.class, new DoubleVectorFieldValue(value)));
        return this;
    }

    /// Adds a new vector field to the document
    ///
    /// @param name  the field name
    /// @param value the field value
    /// @return this builder
    public DocumentBuilder withVectorField(String name, Double[] value) {
        var unboxed = new double[value.length];
        for (int i = 0; i < unboxed.length; i++) {
            unboxed[i] = value[i];
        }
        this.fields.add(new Field<>(name, DoubleVectorFieldValue.class, new DoubleVectorFieldValue(unboxed)));
        return this;
    }

    /// Adds a new String field to the document
    ///
    /// @param name  the field name
    /// @param value the field value
    /// @return this builder
    public DocumentBuilder withStringField(String name, String value) {
        this.fields.add(new Field<>(name, StringFieldValue.class, new StringFieldValue(value)));
        return this;
    }

    /// Adds a new Integer field to the document
    ///
    /// @param name  the field name
    /// @param value the field value
    /// @return this builder
    public DocumentBuilder withIntegerField(String name, Integer value) {
        this.fields.add(new Field<>(name, IntegerFieldValue.class, new IntegerFieldValue(value)));
        return this;
    }

    /// Adds all the fields in the Map. This method handles three value types:
    /// - Map values of type double[] or Double[] will be added as vector fields
    /// - Map values of type Integer will be added as integer fields
    /// - Any other type will be indexed as a string, calling [String#valueOf(Object)] in the value
    ///
    /// @param fields the fields to add to the document
    /// @return this builder
    public DocumentBuilder with(Map<String, Object> fields) {
        fields.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            switch (value) {
                case double[] vectorField -> withVectorField(key, vectorField);
                case Double[] vectorField -> withVectorField(key, vectorField);
                case Integer intField -> withIntegerField(key, intField);
                default -> withStringField(key, String.valueOf(value));
            }
        });
        return this;
    }


    public Document build() {
        var id = DocumentId.newRandomId();
        return new Document(id, fields);
    }

}
