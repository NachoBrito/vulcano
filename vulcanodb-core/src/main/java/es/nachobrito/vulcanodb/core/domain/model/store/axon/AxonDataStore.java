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

package es.nachobrito.vulcanodb.core.domain.model.store.axon;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;
import es.nachobrito.vulcanodb.core.domain.model.store.DataStore;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.index.HnswIndexHandler;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.infrastructure.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.DefaultDocumentDiskStore;
import es.nachobrito.vulcanodb.core.util.TypedProperties;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * The Axon data store provides support for:
 * <ul>
 *     <li>write persistence via {@link DocumentDiskStore} implementations</li>
 *     <li>HNSW indexing for vector fields</li>
 * </ul>
 *
 * @author nacho
 */
public class AxonDataStore implements DataStore {

    private final Map<String, IndexHandler<?>> indexes;
    private final DocumentDiskStore documentDiskStore;

    private AxonDataStore(Map<String, IndexHandler<?>> indexes, DocumentDiskStore documentDiskStore) {
        this.indexes = indexes;
        this.documentDiskStore = documentDiskStore;
    }

    @Override
    public void add(Document document) {
        var result = documentDiskStore
                .write(document)
                .join();

        if (!result.success()) {
            throw new AxonDataStoreException(result.error());
        }
    }

    @Override
    public Optional<Document> get(DocumentId documentId) {
        return documentDiskStore.read(documentId);
    }

    @Override
    public Result search(Query query) {
        return null;
    }

    @Override
    public CompletableFuture<Void> addAsync(Document document) {
        return documentDiskStore
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
                () -> documentDiskStore.read(documentId), ExecutorProvider.defaultExecutor());
    }

    @Override
    public CompletableFuture<Result> searchAsync(Query query) {
        return CompletableFuture.supplyAsync(
                () -> search(query), ExecutorProvider.defaultExecutor());
    }

    @Override
    public void close() throws Exception {
        documentDiskStore.close();
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


    public static class Builder {
        private final Map<String, IndexHandler<?>> indexes = new HashMap<>();
        private final Properties config = new Properties();
        private DocumentDiskStore documentDiskStore;

        public AxonDataStore build() {
            var documentWriter = this.documentDiskStore != null ?
                    this.documentDiskStore :
                    new DefaultDocumentDiskStore(new TypedProperties(config));

            return new AxonDataStore(indexes, documentWriter);
        }

        public Builder withVectorIndex(String fieldName) {
            this.indexes.put(fieldName, new HnswIndexHandler(fieldName));
            return this;
        }

        public Builder withDocumentWriter(DocumentDiskStore documentDiskStore) {
            this.documentDiskStore = documentDiskStore;
            return this;
        }

        public Builder withDataPath(Path dataPath) {
            config.setProperty(ConfigProperties.PROPERTY_PATH, dataPath.toString());
            return this;
        }
    }
}
