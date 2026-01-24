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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a document within the database.
 * A document consists of a unique identifier and a collection of fields.
 *
 * @author nacho
 */
public class Document {
    private final DocumentId id;
    private final Map<String, Field<?, ?>> fields;

    Document(DocumentId id, List<Field<?, ?>> fields) {
        this.id = id;
        this.fields = fields.stream().collect(Collectors.toMap(Field::key, Function.identity()));
    }

    /**
     * Retrieves a field from the document by its name.
     *
     * @param fieldName the name of the field to retrieve
     * @return an {@link Optional} containing the field if found, or empty otherwise
     */
    public Optional<Field<?, ?>> field(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    /**
     * Returns the unique identifier of this document.
     *
     * @return the document ID
     */
    public DocumentId id() {
        return id;
    }

    /**
     * Returns a new builder for creating a {@link Document}.
     *
     * @return a new document builder
     */
    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    /**
     * Returns an unmodifiable map containing all the fields of this document.
     *
     * @return an unmodifiable map of field names to their values
     */
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(fields
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        it -> it.getValue().value()
                )));
    }

    /**
     * Returns a stream of the fields contained in this document.
     *
     * @return a stream of document fields
     */
    public Stream<Field<?, ?>> getfieldsStream() {
        return fields.values().stream();
    }

    /**
     * Returns the shape of this document, describing its structure and field types.
     *
     * @return the document shape
     */
    public DocumentShape getShape() {
        return new DocumentShape(id, fields);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id) && Objects.equals(fields, document.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
