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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public class Document {
    private final DocumentId id;
    private final Map<String, Field<?, ?>> fields;

    Document(DocumentId id, List<Field<?, ?>> fields) {
        this.id = id;
        this.fields = fields.stream().collect(Collectors.toMap(Field::key, Function.identity()));
    }

    public Optional<Field<?, ?>> field(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    public DocumentId id() {
        return id;
    }

    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    /// Build an unmodifiable map with the fields of this document
    ///
    /// @return an unmodifiable map of this document
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
     * .
     *
     * @return a stream of this document's fields
     */
    public Stream<Field<?, ?>> getfieldsStream() {
        return fields.values().stream();
    }
}
