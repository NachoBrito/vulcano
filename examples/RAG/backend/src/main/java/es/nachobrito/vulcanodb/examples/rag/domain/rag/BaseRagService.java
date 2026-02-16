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

package es.nachobrito.vulcanodb.examples.rag.domain.rag;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.ingestion.DocumentSupplier;
import es.nachobrito.vulcanodb.core.ingestion.IngestionResult;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.Dataset;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.DatasetLoader;
import es.nachobrito.vulcanodb.supplier.pdf.DownloadPdfSupplier;
import io.micronaut.context.BeanLocator;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public abstract class BaseRagService implements RagService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final BeanLocator beanLocator;

    private final VulcanoDb vulcanoDb;

    protected BaseRagService(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
        this.vulcanoDb = beanLocator.getBean(VulcanoDb.class);
    }

    @Override
    public IngestionResult ingest(Dataset dataset) {
        log.info("Ingesting dataset {}", dataset);
        var loader = getDataSetLoader(dataset)
                .orElseThrow(() -> new IllegalArgumentException("No dataset loader found for dataset " + dataset));
        var ingestor = vulcanoDb.newDocumentIngestor();
        try (var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()) {

            Stream<DocumentSupplier> suppliers = loader
                    .getDocumentUrls()
                    .map(entry -> new DownloadPdfSupplier(entry.url(), this::embed, httpClient, entry.metadata()))
                    .map(DocumentSupplier.class::cast);

            var result = ingestor.ingest(suppliers);
            if (result.hasErrors()) {
                result.errors().forEach(error ->
                        log.error("Error ingesting document {}: {}", error.document().id(), error.errorMessage())
                );
                throw new RuntimeException("Error ingesting dataset " + dataset);
            }
            log.info("Successfully ingested dataset {}. Total new documents: {}", dataset.name(), result.totalDocuments());
            return result;
        }
    }

    private Optional<DatasetLoader> getDataSetLoader(Dataset dataset) {
        return beanLocator
                .findBean(DatasetLoader.class, Qualifiers.byName(dataset.name()));
    }
}
