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

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.TelemetrySpy;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.Dataset;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.DatasetLoader;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.arxiv.ArxivDataset;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author nacho
 */
class RagServiceIngestDatasetTest {

    private Path path;
    private VulcanoDb vulcanoDb;
    private TelemetrySpy telemetry;
    private DatasetLoader datasetLoader;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        var axon = AxonDataStore
                .builder()
                .withDataFolder(path)
                .build();

        telemetry = new TelemetrySpy();
        vulcanoDb = VulcanoDb
                .builder()
                .withTelemetry(telemetry)
                .withDataStore(axon).build();

        var objectMapper = ObjectMapper.getDefault();
        var resourceResolver = new ResourceResolver();
        var arxiveDatasetPath = "classpath:dataset/arxiv-metadata-oai-snapshot.json";
        datasetLoader = new ArxivDataset(objectMapper, resourceResolver, arxiveDatasetPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        vulcanoDb.close();
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectDatasetIngestionWorks() {
        var service = buildTestService();
        service.ingest(Dataset.ARXIV);

        assertEquals(37 + 18 + 23, vulcanoDb.getDocumentCount().intValue());
        var queueStats = telemetry.getGaugeStats(MetricName.DOCUMENT_INSERT_QUEUE);
        assertTrue(queueStats.getCount() > 0);
        assertTrue(queueStats.getAverage() > 0);
        assertTrue(queueStats.getMax() > 0);

    }

    private RagService buildTestService() {
        var beanLocator = mock(BeanLocator.class);
        doReturn(vulcanoDb)
                .when(beanLocator)
                .getBean(VulcanoDb.class);

        doReturn(Optional.of(datasetLoader))
                .when(beanLocator)
                .findBean(eq(DatasetLoader.class), any(Qualifier.class));

        return new TestService(beanLocator);
    }


    private static final class TestService extends BaseRagService {
        TestService(BeanLocator beanLocator) {
            super(beanLocator);
        }


        @Override
        public void chat(RagQuery query, Consumer<RagTokens> ragTokensConsumer, Consumer<EmbeddingMatch<TextSegment>> onEmbeddingMatch) {

        }

        @Override
        public float[] embed(String text) {
            return new float[]{};
        }
    }
}