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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import es.nachobrito.vulcanodb.core.telemetry.SamplingRate;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.exporter.httpserver.MetricsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author nacho
 */
public final class PrometheusTelemetry extends MicrometerTelemetry {
    private static final Logger log = LoggerFactory.getLogger(PrometheusTelemetry.class);
    private final HTTPServer httpServer;

    /**
     *
     * @param port        The port to publish Prometheus metrics
     * @param enabled     whether this telemetry is enabled
     * @param configLevel the max metric level to publish
     */
    public PrometheusTelemetry(int port, boolean enabled, MetricLevel configLevel, SamplingRate samplingRate) {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        super(registry, enabled, configLevel, samplingRate);
        httpServer = startHttpServer(port, registry);
    }

    private HTTPServer startHttpServer(int port, PrometheusMeterRegistry registry) {
        HTTPServer httpServer;
        try {
            httpServer = HTTPServer.builder()
                    .port(port)
                    .registry(registry.getPrometheusRegistry())
                    .defaultHandler(new CorsHandler(new MetricsHandler(PrometheusProperties.get(), registry.getPrometheusRegistry())))
                    .buildAndStart();
            log.info("HTTP telemetry exposed: http://localhost:{}", port);
            return httpServer;
        } catch (IOException e) {
            log.error("Could not start HTTP server on port {}", port, e);
        }
        return null;
    }

    private record CorsHandler(HttpHandler delegate) implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (log.isDebugEnabled()) {
                    log.debug("OPTIONS request intercepted to the metrics endpoint.");
                }
                exchange.sendResponseHeaders(204, -1);
            } else {
                delegate.handle(exchange);
            }
        }
    }


    @Override
    public void close() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
