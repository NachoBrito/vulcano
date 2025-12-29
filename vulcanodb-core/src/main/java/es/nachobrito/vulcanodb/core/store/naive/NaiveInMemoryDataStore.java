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

package es.nachobrito.vulcanodb.core.store.naive;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.store.DataStore;
import es.nachobrito.vulcanodb.core.store.naive.queryevaluation.QueryOperations;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps all the vectors in an in-memory concurrent hashmap, and implements search with a brute-force approach
 * (calculating the distance between the physical and every vector stored)
 *
 * @author nacho
 */
public class NaiveInMemoryDataStore implements DataStore {
    private final ConcurrentHashMap<DocumentId, Document> documents = new ConcurrentHashMap<>();

    @Override
    public QueryResult search(Query query, int maxResults) {
        var evaluator = QueryOperations.of(query);
        return this.documents
                .values()
                .stream()
                .parallel()
                .map(evaluator.mapper())
                .filter(evaluator.predicate())
                .sorted(evaluator.comparator())
                .limit(maxResults)
                .collect(evaluator.collector());
    }

    @Override
    public void add(Document document) {
        this.documents.put(document.id(), document);
    }

    @Override
    public Optional<Document> get(DocumentId documentId) {
        return Optional.ofNullable(this.documents.get(documentId));
    }

    @Override
    public void close() throws Exception {
        //nothing to do here, this is 100% in-memory storage
    }
}
