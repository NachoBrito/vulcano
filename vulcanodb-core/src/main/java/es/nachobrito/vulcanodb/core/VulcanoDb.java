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
import es.nachobrito.vulcanodb.core.query.Query;
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

    private VulcanoDb(DataStore dataStore) {
        this.dataStore = dataStore;
    }


    /**
     * Adds a new document to the database
     *
     * @param document the document to add to the database
     */
    public void add(Document document) {
        dataStore.add(document);
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
        this.dataStore.remove(documentId);
    }

    /**
     * Finds documents relevant for the provided physical
     *
     * @param query the physical to filter stored documents
     * @return the result containing relevant documents
     */
    public QueryResult search(Query query) {
        return dataStore.search(query);
    }

    /**
     * Finds documents relevant for the provided physical
     *
     * @param query      the physical to filter stored documents
     * @param maxResults the maximum number of documents to return
     * @return the result containing relevant documents
     */
    public QueryResult search(Query query, int maxResults) {
        return dataStore.search(query, maxResults);
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

        public Builder withDataStore(DataStore newDataStore) {
            this.dataStore = newDataStore;
            return this;
        }

        public VulcanoDb build() {
            var vulcanoDb = new VulcanoDb(dataStore);
            vulcanoDb.dataStore.initialize().join();
            return vulcanoDb;
        }

    }
}
