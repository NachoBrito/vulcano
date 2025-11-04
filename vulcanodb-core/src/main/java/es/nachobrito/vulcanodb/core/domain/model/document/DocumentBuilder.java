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

/**
 * @author nacho
 */
public class DocumentBuilder {
    private final List<Field<?>> fields = new ArrayList<>();

    DocumentBuilder() {

    }

    public DocumentBuilder withVectorField(String name, double[] value) {
        this.fields.add(new Field<>(name, DoubleVectorFieldValue.class, new DoubleVectorFieldValue(value)));
        return this;
    }

    public DocumentBuilder withStringField(String name, String value) {
        this.fields.add(new Field<>(name, StringVectorFieldValue.class, new StringVectorFieldValue(value)));
        return this;
    }

    public Document build() {
        var id = DocumentId.newRandomId();
        return new Document(id, fields);
    }

}
