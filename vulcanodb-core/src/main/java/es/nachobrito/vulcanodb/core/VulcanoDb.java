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
import es.nachobrito.vulcanodb.core.observability.MetricName;
import es.nachobrito.vulcanodb.core.observability.MetricsService;
import es.nachobrito.vulcanodb.core.observability.NoOpMetricsService;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.query.QueryBuilder;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.store.DataStore;
import es.nachobrito.vulcanodb.core.store.naive.NaiveInMemoryDataStore;

import java.util.Arrays;

/**
 * Façade class for the Vector Database.
 *
 * @author nacho
 */
public class VulcanoDb implements AutoCloseable {

    private final DataStore dataStore;
    private final MetricsService metrics;

    private VulcanoDb(DataStore dataStore, MetricsService metrics) {
        this.dataStore = dataStore;
        this.metrics = metrics;
    }


    /**
     * Adds a new document to the database
     *
     * @param document the document to add to the database
     */
    public void add(Document document) {
        if (!metrics.isEnabled()) {
            dataStore.add(document);
            return;
        }

        long startTime = System.nanoTime();
        try {
            dataStore.add(document);
        } finally {
            metrics.recordTimer(MetricName.DOCUMENT_INSERT_LATENCY, System.nanoTime() - startTime);
            metrics.incrementCounter(MetricName.DOCUMENT_INSERT_COUNT);
        }
    }

    /**
     * Adds all the documents to the database
     *
     * @param documents the documents to add to the database
     */
    public void add(Document... documents) {
        Arrays.stream(documents).forEach(this::add);
    }

    public void remove(DocumentId documentId) {
        if (!metrics.isEnabled()) {
            dataStore.remove(documentId);
            return;
        }
        long startTime = System.nanoTime();
        try {
            dataStore.remove(documentId);
        } finally {
            metrics.recordTimer(MetricName.DOCUMENT_REMOVE_COUNT, System.nanoTime() - startTime);
            metrics.incrementCounter(MetricName.DOCUMENT_REMOVE_COUNT);
        }
    }

    /**
     * Finds documents relevant for the provided physical
     *
     * @param query the physical to filter stored documents
     * @return the result containing relevant documents
     */
    public QueryResult search(Query query) {
        if (!metrics.isEnabled()) {
            return dataStore.search(query);
        }

        long startTime = System.nanoTime();
        try {
            return dataStore.search(query);
        } finally {
            metrics.recordTimer(MetricName.SEARCH_LATENCY, System.nanoTime() - startTime);
            metrics.incrementCounter(MetricName.SEARCH_COUNT);
        }
    }

    /**
     * Finds documents relevant for the provided physical
     *
     * @param query      the physical to filter stored documents
     * @param maxResults the maximum number of documents to return
     * @return the result containing relevant documents
     */
    public QueryResult search(Query query, int maxResults) {
        if (!metrics.isEnabled()) {
            return dataStore.search(query, maxResults);
        }
        long startTime = System.nanoTime();
        try {
            return dataStore.search(query, maxResults);
        } finally {
            metrics.recordTimer(MetricName.SEARCH_LATENCY, System.nanoTime() - startTime);
            metrics.incrementCounter(MetricName.SEARCH_COUNT);
        }
    }

    public static QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    /**
     * Gets a new builder instance
     *
     * @return a new builder for VulcanoDb objects
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() throws Exception {
        dataStore.close();
    }

    public static final class Builder {
        private DataStore dataStore = new NaiveInMemoryDataStore();
        private MetricsService metricsService = new NoOpMetricsService();

        public Builder withDataStore(DataStore newDataStore) {
            this.dataStore = newDataStore;
            return this;
        }

        public Builder withMetricsService(MetricsService metricsService) {
            this.metricsService = metricsService;
            return this;
        }

        public VulcanoDb build() {
            var vulcanoDb = new VulcanoDb(dataStore, metricsService);
            vulcanoDb.dataStore.initialize().join();
            return vulcanoDb;
        }

    }
}
