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

package es.nachobrito.vulcanodb.core.document;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Objects of this class describe the shape of a document, i.e. the schema (field types and names) plus its Id.
 *
 * @author nacho
 */
public class DocumentShape {
    private static final String VALUE_SEPARATOR = ":";
    private static final String FIELD_SEPARATOR = ";";
    private static final String ID_SEPARATOR = "|";

    private final Map<String, Class<? extends FieldValueType<?>>> fields;
    private final DocumentId documentId;

    DocumentShape(DocumentId documentId, Map<String, Field<?, ?>> fields) {
        this.fields = fields.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        it -> it.getValue().type()
                ));
        this.documentId = documentId;
    }

    private DocumentShape(DocumentId documentId) {
        this.fields = new HashMap<>();
        this.documentId = documentId;
    }

    /**
     * Deserializes the string returned by {@link #toString()} into a new DocumentShape instance.
     *
     * @param shapeString the string, as returned by {@link #toString()}
     * @return the deserialized DocumentShape instance
     */
    public static DocumentShape from(String shapeString) {
        var stringId = shapeString.substring(0, DocumentId.getStringLength());
        var documentId = DocumentId.of(stringId);
        shapeString = shapeString.substring(stringId.length() + ID_SEPARATOR.length());
        var shape = new DocumentShape(documentId);
        var fields = shapeString.split(FIELD_SEPARATOR);
        for (String field : fields) {
            if (field.isBlank()) {
                break;
            }
            var parts = field.split(VALUE_SEPARATOR);
            var fieldName = parts[0];
            var fieldType = parts[1];
            try {
                //noinspection unchecked
                shape.fields.put(fieldName, (Class<? extends FieldValueType<?>>) Class.forName(fieldType));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return shape;
    }

    /**
     *
     * @return a string representation of this document shape.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb
                .append(documentId.toString())
                .append(ID_SEPARATOR);

        fields.forEach((fieldName, fieldType) -> {
            sb
                    .append(fieldName)
                    .append(VALUE_SEPARATOR)
                    .append(fieldType.getName())
                    .append(FIELD_SEPARATOR);
        });
        return sb.toString();
    }


    public Map<String, Class<? extends FieldValueType<?>>> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public DocumentId getDocumentId() {
        return documentId;
    }
}
