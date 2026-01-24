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

package es.nachobrito.vulcanodb.core;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.ingestion.DocumentIngestor;
import es.nachobrito.vulcanodb.core.ingestion.WorkQueueIngestor;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.query.QueryBuilder;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.store.DataStore;
import es.nachobrito.vulcanodb.core.store.naive.NaiveInMemoryDataStore;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.NoOpTelemetry;
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;

import java.util.Arrays;

/**
 * The main entry point for the VulcanoDB vector database.
 * This class provides a high-level API for managing documents, performing similarity searches,
 * and handling database operations.
 *
 * @author nacho
 */
public class VulcanoDb implements AutoCloseable {

    private final DataStore dataStore;
    private final Telemetry telemetry;

    private VulcanoDb(DataStore dataStore, Telemetry telemetry) {
        this.dataStore = dataStore;
        this.telemetry = telemetry;
    }


    /**
     * Adds a document to the database.
     *
     * @param document the document to be indexed
     */
    public void add(Document document) {
        if (!telemetry.isEnabled()) {
            dataStore.add(document);
            return;
        }

        long startTime = System.nanoTime();
        try {
            dataStore.add(document, telemetry);
        } finally {
            telemetry.incrementCounter(MetricName.DOCUMENT_INSERT_COUNT);
            if (telemetry.shouldCapture(MetricName.DOCUMENT_INSERT_LATENCY)) {
                telemetry.recordTimer(MetricName.DOCUMENT_INSERT_LATENCY, System.nanoTime() - startTime);
            }
            if (telemetry.shouldCapture(MetricName.DOCUMENT_COUNT)) {
                telemetry.registerGauge(MetricName.DOCUMENT_COUNT, dataStore::getDocumentCount);
            }
        }
    }

    /**
     * Adds multiple documents to the database.
     *
     * @param documents the documents to be indexed
     */
    public void add(Document... documents) {
        Arrays.stream(documents).forEach(this::add);
    }

    public void remove(DocumentId documentId) {
        if (!telemetry.isEnabled()) {
            dataStore.remove(documentId);
            return;
        }
        long startTime = System.nanoTime();
        try {
            dataStore.remove(documentId, telemetry);
        } finally {
            telemetry.recordTimer(MetricName.DOCUMENT_REMOVE_LATENCY, System.nanoTime() - startTime);
            telemetry.incrementCounter(MetricName.DOCUMENT_REMOVE_COUNT);
            if (telemetry.shouldCapture(MetricName.DOCUMENT_COUNT)) {
                telemetry.registerGauge(MetricName.DOCUMENT_COUNT, dataStore::getDocumentCount);
            }
        }
    }

    /**
     * Searches for documents matching the specified query.
     *
     * @param query the search query criteria
     * @return a {@link QueryResult} containing the matching documents
     */
    public QueryResult search(Query query) {
        if (!telemetry.isEnabled()) {
            return dataStore.search(query);
        }

        long startTime = System.nanoTime();
        try {
            return dataStore.search(query, Integer.MAX_VALUE, telemetry);
        } finally {
            telemetry.recordTimer(MetricName.SEARCH_LATENCY, System.nanoTime() - startTime);
            telemetry.incrementCounter(MetricName.SEARCH_COUNT);
        }
    }

    /**
     * Searches for documents matching the specified query, limiting the number of results returned.
     *
     * @param query      the search query criteria
     * @param maxResults the maximum number of results to return
     * @return a {@link QueryResult} containing the matching documents
     */
    public QueryResult search(Query query, int maxResults) {
        if (!telemetry.isEnabled()) {
            return dataStore.search(query, maxResults);
        }
        long startTime = System.nanoTime();
        try {
            return dataStore.search(query, maxResults, telemetry);
        } finally {
            telemetry.recordTimer(MetricName.SEARCH_LATENCY, System.nanoTime() - startTime);
            telemetry.incrementCounter(MetricName.SEARCH_COUNT);
        }
    }

    /**
     * Creates a new {@link DocumentIngestor} for asynchronous batch document loading.
     *
     * @return a new document ingestor instance
     */
    public DocumentIngestor newDocumentIngestor() {
        return new WorkQueueIngestor(this, telemetry);
    }

    /**
     * Returns a new builder for constructing database queries.
     *
     * @return a new query builder instance
     */
    public static QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    /**
     * Returns a new builder for configuring and creating a {@link VulcanoDb} instance.
     *
     * @return a new database builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() throws Exception {
        dataStore.close();
        telemetry.close();
    }

    public static final class Builder {
        private DataStore dataStore = new NaiveInMemoryDataStore();
        private Telemetry telemetry = new NoOpTelemetry();

        /**
         * Configures the data storage engine to be used by the database.
         *
         * @param newDataStore the data store implementation
         * @return this builder instance
         */
        public Builder withDataStore(DataStore newDataStore) {
            this.dataStore = newDataStore;
            return this;
        }

        /**
         * Configures the telemetry provider for monitoring database metrics and performance.
         *
         * @param telemetry the telemetry implementation
         * @return this builder instance
         */
        public Builder withTelemetry(Telemetry telemetry) {
            this.telemetry = telemetry;
            return this;
        }

        /**
         * Creates a new {@link VulcanoDb} instance with the configured settings.
         *
         * @return a configured database instance
         */
        public VulcanoDb build() {
            var vulcanoDb = new VulcanoDb(dataStore, telemetry);
            vulcanoDb.dataStore.initialize().join();
            return vulcanoDb;
        }

    }
}
