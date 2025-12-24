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

package es.nachobrito.vulcanodb.core.domain.model.store;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author nacho
 */
public interface DataStore extends AutoCloseable {

    /**
     * Adds a new document to the store. If a document with the same id exists, it will be replaced by this one.
     *
     * @param document the document to add
     */
    void add(Document document);

    /**
     * Retrieves a document by its id.
     *
     * @param documentId the document id
     * @return the document, if it exists
     */
    Optional<Document> get(DocumentId documentId);

    /**
     * Finds documents matching the provided query.
     *
     * @param query the query
     * @return the result containing documents matching the query
     */
    Result search(Query query);


    /**
     * Asynchronous version of the {@link #add(Document)} method.
     * <p>
     * Adds a new document to the store. If a document with the same id exists, it will be replaced by this one.
     *
     * @param document the document to add
     */
    default CompletableFuture<Void> addAsync(Document document) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Asynchronous version of the {@link #get(DocumentId)} method
     * <p>
     * Retrieves a document by its id.
     *
     * @param documentId the document id
     * @return the document, if it exists
     */
    default CompletableFuture<Optional<Document>> getAsync(DocumentId documentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Asynchronous version of the {@link #search(Query)} method
     * <p>
     * Finds documents matching the provided query.
     *
     * @param query the query
     * @return the result containing documents matching the query
     */
    default CompletableFuture<Result> searchAsync(Query query) {
        throw new UnsupportedOperationException("Not implemented");
    }

}
