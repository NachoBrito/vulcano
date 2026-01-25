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
import es.nachobrito.vulcanodb.core.document.DocumentMother;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.TelemetrySpy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
class WorkQueueIngestorTest {

    @TempDir
    Path tempDir;

    @Test
    void testIngestDocuments() throws InterruptedException {
        var store = AxonDataStore.builder()
                .withDataFolder(tempDir)
                .build();
        var telemetry = new TelemetrySpy();
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "text", "the text value"
                )).build();

        var shape = exampleDoc.getShape();
        var docs = DocumentMother.random(shape, 100);

        try (var vulcanoDb = VulcanoDb.builder().withDataStore(store).withTelemetry(telemetry).build()) {
            IngestionResult result;
            try (var ingestor = vulcanoDb.newDocumentIngestor()) {
                result = ingestor.ingest(docs);
            }
            assertEquals(docs.size(), result.ingestedDocuments());
            assertEquals(docs.size(), result.totalDocuments());
            assertEquals(0, result.errors().size());
            assertEquals(docs.size(), store.getDocumentCount());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        telemetryAssertions(docs.size(), telemetry);
    }


    @Test
    void testIngestDocumentsFrom() throws InterruptedException {
        var store = AxonDataStore.builder()
                .withDataFolder(tempDir)
                .build();
        var telemetry = new TelemetrySpy();
        var exampleDoc = Document.builder()
                .with(Map.of(
                        "text", "the text value"
                )).build();

        var shape = exampleDoc.getShape();
        var docs = DocumentMother
                .random(shape, 100)
                .stream()
                .map(it -> (Supplier<Document>) () -> it)
                .toList();
        try (var vulcanoDb = VulcanoDb.builder().withDataStore(store).withTelemetry(telemetry).build()) {
            IngestionResult result;
            try (var ingestor = vulcanoDb.newDocumentIngestor()) {
                result = ingestor.ingestFrom(docs);
            }
            assertEquals(docs.size(), result.ingestedDocuments());
            assertEquals(docs.size(), result.totalDocuments());
            assertEquals(0, result.errors().size());
            assertEquals(docs.size(), store.getDocumentCount());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        telemetryAssertions(docs.size(), telemetry);
    }

    private static void telemetryAssertions(int docCount, TelemetrySpy telemetry) throws InterruptedException {
        Thread.sleep(500);

        assertTrue(telemetry.getIsEnabledInvocations() > 0);
        //assertTrue(telemetry.getShouldCaptureLevelInvocations() > 0);
        assertTrue(telemetry.getShouldCaptureMetricInvocations(MetricName.OFF_HEAP_MEMORY_USAGE) > 0);
        assertTrue(telemetry.getShouldCaptureMetricInvocations(MetricName.DOCUMENT_INSERT_LATENCY) > 0);
        assertTrue(telemetry.getGaugeStats(MetricName.STORED_DOCUMENTS).getMax() > 0);
        assertEquals(docCount, telemetry.getCounter(MetricName.DOCUMENT_INSERT_COUNT));
        var insertStats = telemetry.getTimerStats(MetricName.DOCUMENT_INSERT_LATENCY);
        assertTrue(insertStats.getAverage() > 0);
        assertTrue(insertStats.getCount() > 0);

        var memStats = telemetry.getGaugeStats(MetricName.OFF_HEAP_MEMORY_USAGE);
        assertTrue(memStats.getAverage() > 0);
        assertTrue(memStats.getCount() > 0);

        var queueStats = telemetry.getGaugeStats(MetricName.DOCUMENT_INSERT_QUEUE);
        assertTrue(queueStats.getAverage() > 0);
        assertTrue(queueStats.getCount() > 0);
    }

}