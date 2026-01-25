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

package es.nachobrito.vulcanodb.core.ingestion;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A {@link DocumentIngestor} implementation that uses a work queue for background ingestion.
 * This ingestor enables asynchronous processing and indexing of documents.
 *
 * @author nacho
 */
public class WorkQueueIngestor implements DocumentIngestor {
    private final Logger log = LoggerFactory.getLogger(WorkQueueIngestor.class);
    private final VulcanoDb vulcanoDb;
    private final Telemetry telemetry;

    private final ExecutorService executorService;
    private final AtomicInteger queueSize = new AtomicInteger(0);

    /**
     * Creates a new work queue ingestor.
     *
     * @param vulcanoDb the database instance where documents will be ingested
     * @param telemetry the telemetry provider for monitoring ingestion metrics
     */
    public WorkQueueIngestor(VulcanoDb vulcanoDb, Telemetry telemetry) {
        this.vulcanoDb = vulcanoDb;
        this.telemetry = telemetry;
        executorService = Executors.newVirtualThreadPerTaskExecutor();
        if (telemetry.isEnabled()) {
            telemetry.registerGauge(MetricName.DOCUMENT_INSERT_QUEUE, queueSize::get);
        }
    }

    @Override
    public IngestionResult ingest(Collection<Document> documents) {
        if (telemetry.isEnabled()) {
            queueSize.addAndGet(documents.size());
        }

        if (documents.size() < Runtime.getRuntime().availableProcessors()) {
            return ingestSynchronously(documents);
        }

        AtomicInteger ingested = new AtomicInteger(0);
        Set<IngestionError> errors = ConcurrentHashMap.newKeySet();

        var futures = documents
                .stream()
                .map(document -> toCompletableFuture(document, ingested, errors))
                .toArray(CompletableFuture[]::new);

        CompletableFuture
                .allOf(futures)
                .join();

        return new IngestionResult(documents.size(), ingested.get(), errors);
    }

    private CompletableFuture<Void> toCompletableFuture(Document document, AtomicInteger ingested, Set<IngestionError> errors) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("{} -> Ingesting document {}", Thread.currentThread().threadId(), document.id());
                }
                vulcanoDb.add(document);
                ingested.incrementAndGet();
                if (telemetry.isEnabled()) {
                    queueSize.decrementAndGet();
                }
            } catch (Throwable throwable) {
                errors.add(new IngestionError(document, throwable.getMessage()));
            }
        }, executorService);
    }

    private IngestionResult ingestSynchronously(Collection<Document> documents) {
        int ingested = 0;
        var errors = new HashSet<IngestionError>();
        for (Document document : documents) {
            try {
                vulcanoDb.add(document);
                ingested++;
            } catch (Throwable throwable) {
                errors.add(new IngestionError(document, throwable.getMessage()));
            }
        }
        return new IngestionResult(documents.size(), ingested, errors);
    }

    @Override
    public IngestionResult ingestFrom(Collection<Supplier<Document>> suppliers) {
        if (telemetry.isEnabled()) {
            queueSize.addAndGet(suppliers.size());
        }
        AtomicInteger ingested = new AtomicInteger(0);
        Set<IngestionError> errors = ConcurrentHashMap.newKeySet();

        var futures = suppliers
                .stream()
                .map(supplier -> toCompletableFuture(supplier, ingested, errors))
                .toArray(CompletableFuture[]::new);

        CompletableFuture
                .allOf(futures)
                .join();

        return new IngestionResult(suppliers.size(), ingested.get(), errors);
    }

    private CompletableFuture<Void> toCompletableFuture(Supplier<Document> supplier, AtomicInteger ingested, Set<IngestionError> errors) {
        return CompletableFuture.runAsync(() -> {
            Document document = null;
            try {
                if (log.isDebugEnabled()) {
                    log.debug("{} -> Invoking supplier {}", Thread.currentThread().threadId(), supplier);
                }
                document = supplier.get();
                vulcanoDb.add(document);
                ingested.incrementAndGet();
                if (telemetry.isEnabled()) {
                    queueSize.decrementAndGet();
                }
            } catch (Throwable throwable) {
                errors.add(new IngestionError(document, throwable.getMessage()));
            }
        }, executorService);
    }

    @Override
    public void close() throws Exception {
        if (executorService != null) {
            executorService.close();
        }
    }
}
