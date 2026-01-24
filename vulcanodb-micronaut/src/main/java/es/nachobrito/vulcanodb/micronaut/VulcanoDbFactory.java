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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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
    private final MicronautTelemetry micronautTelemetry;

    public VulcanoDbFactory(
            @Value("${vulcanodb.datafolder:./vulcanodb-data}")
            String dataFolder,
            @Value("${vulcanodb.index.vector:}")
            String vectorIndexes,
            @Value("${vulcanodb.index.string:}")
            String stringIndexes, MicronautTelemetry micronautTelemetry) {

        this.dataFolder = Path.of(dataFolder);
        this.vectorIndexes = vectorIndexes.split(",");
        this.stringIndexes = stringIndexes.split(",");
        this.micronautTelemetry = micronautTelemetry;
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
        return VulcanoDb
                .builder()
                .withDataStore(buildDataStore())
                .withTelemetry(micronautTelemetry)
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
