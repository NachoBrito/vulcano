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
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.ExecutionContext;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.IndexRegistry;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.QueryExecutor;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LogicalNode;
import es.nachobrito.vulcanodb.core.store.axon.wal.DefaultWalManager;
import es.nachobrito.vulcanodb.core.store.axon.wal.WalManager;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static es.nachobrito.vulcanodb.core.store.axon.wal.WalEntry.Type;

/**
 * The Axon data store provides support for:
 * <ul>
 *     <li>Write persistence via {@link DocumentPersister} implementations</li>
 *     <li>HNSW indexing for vector fields</li>
 * </ul>
 *
 * @author nacho
 */
public class AxonDataStore implements DataStore, IndexRegistry {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<String, IndexHandler<?>> indexes;
    private final DocumentPersister documentPersister;
    private final QueryExecutor queryExecutor;
    private final WalManager walManager;
    private boolean initialized = false;

    private AxonDataStore(Map<String, IndexHandler<?>> indexes, DocumentPersister documentPersister, WalManager walManager) {
        this.indexes = indexes;
        this.documentPersister = documentPersister;
        this.walManager = walManager;
        var ctx = new ExecutionContext(
                documentPersister,
                Collections.unmodifiableMap(indexes));
        this.queryExecutor = new QueryExecutor(ctx, this);
        log.info("Axon Datastore created.");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        if (this.initialized) {
            log.info("Axon Datastore already initialized, skipping initialization call.");
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            log.info("Starting initialization process, recovering from WAL if needed");

            recoverUncommitedOperations();

            initialized = true;
            log.info("Initialization complete");
        });
    }

    private void recoverUncommitedOperations() {
        try {
            var uncommitted = walManager.readUncommitted();
            if (!uncommitted.isEmpty()) {
                log.info("Found {} uncommitted transactions in WAL. Recovering...", uncommitted.size());
                for (var entry : uncommitted) {
                    try {
                        if (entry.type() == Type.ADD) {
                            entry.document().ifPresent(this::addInternal);
                        } else if (entry.type() == Type.REMOVE) {
                            entry.documentId().ifPresent(id -> this.remove(DocumentId.of(id)));
                        }
                        walManager.commit(entry.txId());
                    } catch (Exception e) {
                        log.error("Failed to recover WAL entry {}", entry.txId(), e);
                    }
                }
                log.info("Recovery complete.");
            }
        } catch (IOException e) {
            log.error("Could not read WAL during initialization", e);
        }
    }

    @Override
    public void add(Document document) {
        try {
            long txId = walManager.recordAdd(document);
            addInternal(document);
            walManager.commit(txId);
        } catch (IOException e) {
            throw new AxonDataStoreException(e);
        }
    }

    private void addInternal(Document document) {
        var result = documentPersister
                .write(document)
                .join();

        if (!result.success()) {
            throw new AxonDataStoreException(result.error());
        }
        indexFields(result.internalId(), document);
    }

    @Override
    public Optional<Document> get(DocumentId documentId) {
        return documentPersister.read(documentId);
    }

    @Override
    public QueryResult search(Query query, int maxResults) {
        var logicalQueryRoot = LogicalNode.of(query);
        return queryExecutor.execute(logicalQueryRoot, maxResults);
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
                            indexFields(result.internalId(), document);
                        },
                        ExecutorProvider.defaultExecutor()
                );
    }

    private void indexFields(long internalId, Document document) {
        document
                .getfieldsStream()
                .filter(field -> isIndexed(field.key()))
                .forEach(field -> indexes.get(field.key()).index(internalId, document));
    }

    @Override
    public CompletableFuture<Optional<Document>> getAsync(DocumentId documentId) {
        return CompletableFuture.supplyAsync(
                () -> documentPersister.read(documentId), ExecutorProvider.defaultExecutor());
    }

    @Override
    public void remove(DocumentId documentId) {
        try {
            long txId = walManager.recordRemove(documentId.toString());
            this.documentPersister.remove(documentId);
            walManager.commit(txId);
        } catch (IOException e) {
            throw new AxonDataStoreException(e);
        }
    }


    @Override
    public void close() throws Exception {
        log.info("Closing Axon Datastore...");
        walManager.close();
        documentPersister.close();
        log.info("Document persister closed.");
        var errors = new HashMap<String, Exception>();
        for (var entry : indexes.entrySet()) {
            try {
                entry.getValue().close();
                log.info("Index '{}' closed.", entry.getKey());
            } catch (Exception exception) {
                errors.put(entry.getKey(), exception);
            }
        }
        if (!errors.isEmpty()) {
            log.error("Could not close datastore.");
            throw new AxonDataStoreCloseException("Some Index Handlers could not  be closed", errors);
        }
        log.info("Axon Datastore closed successfully.");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isIndexed(String fieldName) {
        return this.indexes.containsKey(fieldName);
    }


    public static class Builder {
        private Path dataFolder = Path.of(System.getProperty("user.home") + "/.VulcanoDB/AxonDS");

        private final Map<String, HnswConfig> indexConfigs = new HashMap<>();

        public AxonDataStore build() {
            var documentPersister = new DefaultDocumentPersister(dataFolder);
            try {
                var walManager = new DefaultWalManager(dataFolder.resolve("wal"));
                return new AxonDataStore(buildIndexHandlers(), documentPersister, walManager);
            } catch (IOException e) {
                throw new AxonDataStoreException(e);
            }
        }

        private Map<String, IndexHandler<?>> buildIndexHandlers() {
            return indexConfigs
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                        var indexFolder = dataFolder
                                .resolve("index")
                                .resolve(FileUtils.toLegalFileName(entry.getKey()));
                        return new HnswIndexHandler(entry.getKey(), entry.getValue(), indexFolder);
                    }));
        }

        public Builder withVectorIndex(String fieldName) {
            this.indexConfigs.put(fieldName, HnswConfig.builder().build());
            return this;
        }

        public Builder withVectorIndex(String fieldName, HnswConfig hnswConfig) {
            this.indexConfigs.put(fieldName, hnswConfig);
            return this;
        }


        public Builder withDataFolder(Path dataFolder) {
            if (!dataFolder.toFile().isDirectory() && !dataFolder.toFile().mkdirs()) {
                throw new IllegalArgumentException("Could not create data folder %s".formatted(dataFolder));
            }
            this.dataFolder = dataFolder;
            return this;
        }

    }
}
