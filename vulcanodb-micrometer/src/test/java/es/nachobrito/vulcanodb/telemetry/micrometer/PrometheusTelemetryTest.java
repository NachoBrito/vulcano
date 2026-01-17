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

package es.nachobrito.vulcanodb.telemetry.micrometer;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.store.naive.NaiveInMemoryDataStore;
import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
class PrometheusTelemetryTest {

    @Test
    void expectPrometheusMetricsPublished() {
        try (var db = buildVulcanoDb()) {
            var document = Document.builder().withStringField("name", "test document").build();
            db.add(document);


            var request = HttpRequest
                    .newBuilder()
                    .uri(new URI("http://localhost:9999/metrics"))
                    .GET()
                    .build();
            var response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            assertNotNull(response);
            assertTrue(response.body().contains("vulcanodb_document_inserts_total 1.0"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private VulcanoDb buildVulcanoDb() throws IOException {
        return VulcanoDb
                .builder()
                .withDataStore(new NaiveInMemoryDataStore())
                .withTelemetry(new PrometheusTelemetry(9999, true, MetricLevel.DIAGNOSTIC))
                .build();
    }
}