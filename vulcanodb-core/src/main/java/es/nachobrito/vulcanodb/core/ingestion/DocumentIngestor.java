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

import es.nachobrito.vulcanodb.core.document.Document;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for components responsible for importing documents into the database.
 * Implementations should handle document processing and storage asynchronously.
 *
 * @author nacho
 */
public interface DocumentIngestor extends AutoCloseable {

    /**
     * Ingests the provided documents.
     *
     * @param documents the documents to ingest
     * @return a {@link CompletableFuture} that will be completed when the ingestion process finishes
     */
    IngestionResult ingest(Collection<Document> documents);

    /**
     * Ingests documents produced by the provided suppliers. Each supplier is invoked
     * within the ingestor's executor service to fetch or create documents before ingestion.
     *
     * @param suppliers the supplier functions that produce the documents to ingest
     * @return a {@link CompletableFuture} that will be completed when all documents have been ingested
     */
    IngestionResult ingestFrom(Collection<DocumentSupplier> suppliers);
}
