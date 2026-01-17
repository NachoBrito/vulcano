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

package es.nachobrito.vulcanodb.micronaut;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.store.DataStore;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;
import es.nachobrito.vulcanodb.telemetry.micrometer.PrometheusTelemetry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author nacho
 */
@Factory
@Requires(property = "vulcanodb.enabled", value = "true")
public class VulcanoDbFactory {

    private final boolean telemetryEnabled;
    private final MetricLevel metricLevel;
    private final int telemetryPort;
    private final Path dataFolder;
    private final String[] vectorIndexes;
    private final String[] stringIndexes;

    public VulcanoDbFactory(
            @Value("${vulcanodb.telemetry.enabled:false}")
            String telemetryEnabled,
            @Value("${vulcanodb.telemetry.level:BASIC}")
            String metricLevel,
            @Value("${vulcanodb.telemetry.port:9999}")
            String telemetryPort,
            @Value("${vulcano.datafolder:./vulcanodb-data")
            String dataFolder,
            @Value("${vulcano.indexes.vector:")
            String vectorIndexes,
            @Value("${vulcano.indexes.string:")
            String stringIndexes) {
        this.telemetryEnabled = Boolean.parseBoolean(telemetryEnabled);
        this.metricLevel = MetricLevel.valueOf(metricLevel.toUpperCase());
        this.telemetryPort = Integer.parseInt(telemetryPort);
        this.dataFolder = Path.of(dataFolder);
        this.vectorIndexes = vectorIndexes.split(",");
        this.stringIndexes = stringIndexes.split(",");
    }

    @Singleton
    public VulcanoDb getVulcanoDb() throws IOException {
        return VulcanoDb
                .builder()
                .withDataStore(buildDataStore())
                .withTelemetry(buildTelemetry())
                .build();
    }

    private Telemetry buildTelemetry() throws IOException {
        return new PrometheusTelemetry(telemetryPort, telemetryEnabled, metricLevel);
    }

    private DataStore buildDataStore() {
        var builder = AxonDataStore.builder().withDataFolder(dataFolder);
        for (String vectorIndex : vectorIndexes) {
            builder.withVectorIndex(vectorIndex);
        }
        for (String stringIndex : stringIndexes) {
            builder.withStringIndex(stringIndex);
        }
        return builder.build();
    }
}
