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
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.store.DataStore;
import es.nachobrito.vulcanodb.core.store.axon.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreCloseException;
import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.store.axon.index.HnswIndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.ExecutionContext;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.IndexRegistry;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.QueryExecutor;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LogicalNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The Axon data store provides support for:
 * <ul>
 *     <li>write persistence via {@link DocumentPersister} implementations</li>
 *     <li>HNSW indexing for vector fields</li>
 * </ul>
 *
 * @author nacho
 */
public class AxonDataStore implements DataStore, IndexRegistry {

    private final Map<String, IndexHandler<?>> indexes;
    private final DocumentPersister documentPersister;
    private final QueryExecutor queryExecutor;

    private AxonDataStore(Map<String, IndexHandler<?>> indexes, DocumentPersister documentPersister) {
        this.indexes = indexes;
        this.documentPersister = documentPersister;
        this.queryExecutor = new QueryExecutor(new ExecutionContext(documentPersister), this);
    }

    @Override
    public void add(Document document) {
        var result = documentPersister
                .write(document)
                .join();

        if (!result.success()) {
            throw new AxonDataStoreException(result.error());
        }
    }

    @Override
    public Optional<Document> get(DocumentId documentId) {
        return documentPersister.read(documentId);
    }

    @Override
    public QueryResult search(Query query) {
        var logicalQueryRoot = LogicalNode.of(query);
        return queryExecutor.execute(logicalQueryRoot);
    }

    @Override
    public CompletableFuture<Void> addAsync(Document document) {
        return documentPersister
                .write(document)
                .thenAcceptAsync(
                        result -> {
                            if (!result.success()) {
                                throw new AxonDataStoreException(result.error());
                            }
                        },
                        ExecutorProvider.defaultExecutor()
                );
    }

    @Override
    public CompletableFuture<Optional<Document>> getAsync(DocumentId documentId) {
        return CompletableFuture.supplyAsync(
                () -> documentPersister.read(documentId), ExecutorProvider.defaultExecutor());
    }

    @Override
    public CompletableFuture<QueryResult> searchAsync(Query query) {
        return CompletableFuture.supplyAsync(
                () -> search(query), ExecutorProvider.defaultExecutor());
    }

    @Override
    public void close() throws Exception {
        documentPersister.close();
        var errors = new HashMap<IndexHandler<?>, Exception>();
        for (IndexHandler<?> indexHandler : indexes.values()) {
            try {
                indexHandler.close();
            } catch (Exception exception) {
                errors.put(indexHandler, exception);
            }
        }
        if (!errors.isEmpty()) {
            throw new AxonDataStoreCloseException("Some Index Handlers could not  be closed", errors);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isIndexed(String fieldName) {
        return this.indexes.containsKey(fieldName);
    }


    public static class Builder {
        private final Map<String, IndexHandler<?>> indexes = new HashMap<>();
        private DocumentPersister documentPersister;

        public AxonDataStore build() {
            if (documentPersister == null) {
                throw new AxonDataStoreException("No DocumentPersister provided");
            }
            return new AxonDataStore(indexes, documentPersister);
        }

        public Builder withVectorIndex(String fieldName) {
            this.indexes.put(fieldName, new HnswIndexHandler(fieldName));
            return this;
        }

        public Builder withDocumentWriter(DocumentPersister documentPersister) {
            this.documentPersister = documentPersister;
            return this;
        }

    }
}
