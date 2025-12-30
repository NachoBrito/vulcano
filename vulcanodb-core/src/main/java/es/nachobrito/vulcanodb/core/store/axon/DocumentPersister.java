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

package es.nachobrito.vulcanodb.core.store.axon;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.document.FieldValueType;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 *
 * Implementations of this class are expected to use internal Long ids for efficiency.
 *
 * @author nacho
 */
public interface DocumentPersister extends AutoCloseable {

    /**
     * Persist the provided document asynchronously.
     *
     * @param document the document to persist
     * @return the CompletableFuture of this task.
     */
    CompletableFuture<DocumentWriteResult> write(Document document);

    /**
     * Retrieve the document by its id, if it exists.
     *
     * @param documentId the document id
     * @return the document associated to the provided id, if any
     */
    Optional<Document> read(DocumentId documentId);

    /**
     * Reads a document by its internal id, if it exists.
     *
     * @param internalId the internal id
     * @return the document associated to the provided id, if any
     */
    Optional<Document> read(long internalId);

    /**
     * Returns a Stream of all the internal ids, for fast read operations
     *
     * @return a Stream of the internal document ids
     */
    Stream<Long> internalIds();

    /**
     * Reads a single value of a document, identified by its internal id
     *
     * @param internalId the internal id of the document (as returned by {@link #internalIds()}
     * @param fieldName  the name of the field to read
     * @param valueType  the value type
     * @param <T>        the expected type of the field value
     * @return the value associated to the field in the document, if any.
     */
    <T> Optional<T> readDocumentField(long internalId, String fieldName, Class<? extends FieldValueType<T>> valueType);

}
