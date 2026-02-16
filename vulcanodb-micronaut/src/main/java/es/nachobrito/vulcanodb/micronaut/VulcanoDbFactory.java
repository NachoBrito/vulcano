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
import es.nachobrito.vulcanodb.core.telemetry.SamplingRate;
import es.nachobrito.vulcanodb.telemetry.micrometer.PrometheusTelemetry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author nacho
 */
@Factory
@Requires(property = "vulcanodb.enabled", value = "true")
public final class VulcanoDbFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Path dataFolder;
    private final String[] vectorIndexes;
    private final String[] stringIndexes;

    private final int telemetryPort;
    private final boolean telemetryEnabled;
    private final MetricLevel telemetryMetricLevel;
    private final SamplingRate telemetrySamplingRate;

    public VulcanoDbFactory(
            @Property(name = "vulcanodb.datafolder", defaultValue = "./vulcanodb-data")
            String dataFolder,
            @Property(name = "vulcanodb.index.vector", defaultValue = "")
            String vectorIndexes,
            @Property(name = "vulcanodb.index.string", defaultValue = "")
            String stringIndexes,
            @Property(name = "vulcanodb.telemetry.port", defaultValue = "9999")
            String telemetryPort,
            @Property(name = "vulcanodb.telemetry.enabled", defaultValue = "true")
            String telemetryEnabled,
            @Property(name = "vulcanodb.telemetry.string", defaultValue = "BASIC")
            String telemetryMetricLevel,
            @Property(name = "vulcanodb.telemetry.string", defaultValue = "MEDIUM")
            String telemetrySamplingRate) {

        this.dataFolder = Path.of(dataFolder);
        this.vectorIndexes = vectorIndexes.split(",");
        this.stringIndexes = stringIndexes.split(",");
        this.telemetryPort = Integer.parseInt(telemetryPort);
        this.telemetryEnabled = Boolean.parseBoolean(telemetryEnabled);
        this.telemetryMetricLevel = MetricLevel.valueOf(telemetryMetricLevel);
        this.telemetrySamplingRate = SamplingRate.valueOf(telemetrySamplingRate);
    }

    @Singleton
    public VulcanoDb getVulcanoDb() throws IOException {
        log.info("""
                \n*** Initializing VulcanoDb ***
                - data folder: {}
                - vector indexes: {}
                - string indexes: {}
                *******************************
                """, dataFolder, vectorIndexes, stringIndexes);

        var telemetry = new PrometheusTelemetry(
                telemetryPort,
                telemetryEnabled,
                telemetryMetricLevel,
                telemetrySamplingRate
        );
        return VulcanoDb
                .builder()
                .withDataStore(buildDataStore())
                .withTelemetry(telemetry)
                .build();
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
