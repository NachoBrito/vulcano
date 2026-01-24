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
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for document storage engines.
 * A data store is responsible for persisting documents and performing efficient searches.
 *
 * @author nacho
 */
public interface DataStore extends AutoCloseable {

    /**
     * Initializes the data store.
     * This may involve building in-memory structures or opening file handles.
     *
     * @return a {@link CompletableFuture} that completes when initialization is finished
     */
    CompletableFuture<Void> initialize();

    /**
     * Adds a document to the store. If a document with the same ID already exists, it will be overwritten.
     *
     * @param document the document to add
     */
    void add(Document document);

    /**
     * Adds a document to the store and records performance metrics.
     *
     * @param document the document to add
     * @param metrics  the telemetry provider
     */
    default void add(Document document, Telemetry metrics) {
        //default implementation does not publish any internal metrics.
        add(document);
    }

    /**
     * Retrieves a document by its unique identifier.
     *
     * @param documentId the document ID
     * @return an {@link Optional} containing the document if found, or empty otherwise
     */
    Optional<Document> get(DocumentId documentId);

    /**
     * Searches for documents matching the specified query.
     *
     * @param query the search query criteria
     * @return a {@link QueryResult} containing the matching documents
     */
    default QueryResult search(Query query) {
        return search(query, Integer.MAX_VALUE);
    }


    /**
     * Searches for documents matching the specified query, limiting the number of results returned.
     *
     * @param query      the search query criteria
     * @param maxResults the maximum number of results to return
     * @return a {@link QueryResult} containing the matching documents
     */
    QueryResult search(Query query, int maxResults);


    /**
     * Searches for documents matching the specified query and records performance metrics.
     *
     * @param query      the search query criteria
     * @param maxResults the maximum number of results to return
     * @param metrics     the telemetry provider
     * @return a {@link QueryResult} containing the matching documents
     */
    default QueryResult search(Query query, int maxResults, Telemetry metrics) {
        //default implementation does not publish any internal metrics.
        return search(query, maxResults);
    }

    /**
     * Removes the document with the specified identifier from the store.
     *
     * @param documentId the ID of the document to remove
     */
    void remove(DocumentId documentId);

    /**
     * Removes the document with the specified identifier and records performance metrics.
     *
     * @param documentId the ID of the document to remove
     * @param metrics    the telemetry provider
     */
    default void remove(DocumentId documentId, Telemetry metrics) {
        //default implementation does not publish any internal metrics.
        remove(documentId);
    }

    /**
     * Returns the total number of documents currently stored in the data store.
     *
     * @return the total document count
     */
    long getDocumentCount();

}
