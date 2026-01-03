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

package es.nachobrito.vulcanodb.core.store;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.QueryResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author nacho
 */
public interface DataStore extends AutoCloseable {

    /**
     * Some DataStore implementations will need to perform an initialization process, like building in-memory
     * data structures used at runtime. Implementations of this method will execute such operations asynchronously.
     *
     * @return the CompletableFuture for this initialization process
     */
    CompletableFuture<Void> initialize();

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
    default QueryResult search(Query query) {
        return search(query, Integer.MAX_VALUE);
    }


    /**
     * Finds documents matching the provided query.
     *
     * @param query      the query
     * @param maxResults the maximum number of documents to return
     * @return the result containing documents matching the query
     */
    QueryResult search(Query query, int maxResults);

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
     * Removes the document associated to the provided id
     *
     * @param documentId the document id to remove.
     */
    void remove(DocumentId documentId);
}
