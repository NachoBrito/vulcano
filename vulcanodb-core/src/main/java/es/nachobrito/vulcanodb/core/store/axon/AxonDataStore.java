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
import es.nachobrito.vulcanodb.core.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswIndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.index.string.StringIndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStoreProvider;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.ExecutionContext;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.IndexRegistry;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.QueryExecutor;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LogicalNode;
import es.nachobrito.vulcanodb.core.telemetry.MetricValue;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
    private boolean initialized = false;
    private final AtomicLong storedDocuments = new AtomicLong();
    private final AtomicLong offHeapBytesCount = new AtomicLong();
    private Future<Void> pendingOffHeapCountOperation = null;

    private AxonDataStore(Map<String, IndexHandler<?>> indexes, DocumentPersister documentPersister) {
        this.indexes = indexes;
        this.documentPersister = documentPersister;
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

            countDocuments();
            countOffHeapBytes();

            initialized = true;
            log.info("Initialization complete");
        }, ExecutorProvider.ingestionExecutor());
    }

    private void countDocuments() {
        storedDocuments.set(this.documentPersister.internalIds().count());
    }

    private void countOffHeapBytes() {

        long documentOffHeapMemory = documentPersister.getOffHeapBytes();
        long indexOffHeapMemory = indexes
                .values()
                .stream()
                .mapToLong(IndexHandler::offHeapBytes)
                .sum();
        if (log.isDebugEnabled()) {
            log.debug("""
                    ************************
                    Counting off heap bytes:
                    - documentOffHeapMemory: {}
                    - indexOffHeapMemory: {}
                    """, documentOffHeapMemory, indexOffHeapMemory);
        }
        offHeapBytesCount.set(documentOffHeapMemory + indexOffHeapMemory);
    }

    @Override
    public void add(Document document) {
        addInternal(document);
        ExecutorProvider.ingestionExecutor().execute(this::countDocuments);
        ExecutorProvider.ingestionExecutor().execute(this::scheduleOffHeapByteCount);
    }

    private void scheduleOffHeapByteCount() {
        if (pendingOffHeapCountOperation != null && !pendingOffHeapCountOperation.isDone()) {
            if (log.isDebugEnabled()) {
                log.debug("Off heap bytes count operation is already scheduled.");
            }
            return;
        }
        //noinspection unchecked
        pendingOffHeapCountOperation = (Future<Void>) ExecutorProvider
                .maintenanceExecutor()
                .submit(this::countOffHeapBytes);
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
        QueryResult result = null;
        var task = ExecutorProvider.queryExecutor().submit(() -> {
            var logicalQueryRoot = LogicalNode.of(query);
            return queryExecutor.execute(logicalQueryRoot, maxResults);
        });
        try {
            result = task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new AxonDataStoreException(e);
        }
        return result;
    }


    private void indexFields(long internalId, Document document) {
        var futures = document
                .getfieldsStream()
                .filter(field -> isIndexed(field.key()))
                .map(field -> CompletableFuture.runAsync(
                        () -> indexes.get(field.key()).index(internalId, document),
                        ExecutorProvider.indexExecutor()))
                .toArray(CompletableFuture[]::new);

        if (futures.length > 0) {
            CompletableFuture.allOf(futures).join();
        }
    }


    @Override
    public void remove(DocumentId documentId) {
        this.documentPersister.remove(documentId);
        ExecutorProvider.ingestionExecutor().execute(this::countDocuments);
    }

    @Override
    public MetricValue getOffHeapMemoryUsage() {
        return new MetricValue(offHeapBytesCount);
    }


    @Override
    public void close() throws Exception {
        log.info("Closing Axon Datastore...");
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
        ExecutorProvider.ingestionExecutor().close();
        ExecutorProvider.indexExecutor().close();
        ExecutorProvider.maintenanceExecutor().close();
        ExecutorProvider.queryExecutor().close();
        log.info("Axon Datastore closed successfully.");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isIndexed(String fieldName) {
        return this.indexes.containsKey(fieldName);
    }

    @Override
    public MetricValue getDocumentCount() {
        return new MetricValue(storedDocuments);
    }


    public static class Builder {
        private Path dataFolder = Path.of(System.getProperty("user.home") + "/.VulcanoDB/AxonDS");

        private final Map<String, HnswConfig> vectorIndexConfigs = new HashMap<>();
        private final List<String> stringIndexes = new ArrayList<>();

        public AxonDataStore build() {
            var keyValueStoreProvider = new KeyValueStoreProvider(dataFolder);
            var documentPersister = new DefaultDocumentPersister(keyValueStoreProvider);
            return new AxonDataStore(buildIndexHandlers(keyValueStoreProvider), documentPersister);
        }

        private Map<String, IndexHandler<?>> buildIndexHandlers(KeyValueStoreProvider keyValueStoreProvider) {
            Map<String, IndexHandler<?>> handlers = vectorIndexConfigs
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                        var indexFolder = dataFolder
                                .resolve("index")
                                .resolve(FileUtils.toLegalFileName(entry.getKey()));
                        var metadataStore = keyValueStoreProvider.getKeyValueStore("hnsw:" + entry.getKey());
                        return new HnswIndexHandler(entry.getKey(), entry.getValue(), indexFolder, metadataStore);
                    }));

            stringIndexes.forEach(fieldName -> {
                var indexFolder = dataFolder
                        .resolve("index")
                        .resolve(FileUtils.toLegalFileName(fieldName));
                var invertedIndexStore = keyValueStoreProvider.getKeyValueStore("inverted-index:" + fieldName);
                handlers.put(fieldName, new StringIndexHandler(fieldName, invertedIndexStore));
            });

            return handlers;
        }

        public Builder withVectorIndex(String fieldName) {
            this.vectorIndexConfigs.put(fieldName, HnswConfig.builder().build());
            return this;
        }

        public Builder withVectorIndex(String fieldName, HnswConfig hnswConfig) {
            this.vectorIndexConfigs.put(fieldName, hnswConfig);
            return this;
        }

        public Builder withStringIndex(String fieldName) {
            this.stringIndexes.add(fieldName);
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
