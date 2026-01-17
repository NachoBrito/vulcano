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

import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

import java.io.IOException;

/**
 * @author nacho
 */
public final class PrometheusTelemetry extends MicrometerTelemetry {
    private final HTTPServer httpServer;

    /**
     *
     * @param port        The port to publish Prometheus metrics
     * @param enabled     whether this telemetry is enabled
     * @param configLevel the max metric level to publish
     * @throws IOException if the server cannot be opened in the provided port
     */
    public PrometheusTelemetry(int port, boolean enabled, MetricLevel configLevel) throws IOException {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        super(registry, enabled, configLevel);
        httpServer = HTTPServer.builder()
                .port(port)
                .registry(registry.getPrometheusRegistry())
                .buildAndStart();
    }


    @Override
    public void close() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
