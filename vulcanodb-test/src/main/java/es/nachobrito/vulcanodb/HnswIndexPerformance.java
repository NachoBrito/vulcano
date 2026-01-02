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

package es.nachobrito.vulcanodb;

import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.store.axon.ConfigProperties;
import es.nachobrito.vulcanodb.core.store.axon.DefaultDocumentPersister;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import es.nachobrito.vulcanodb.core.util.TypedProperties;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * @author nacho
 */
public class HnswIndexPerformance {
    private static Path path;


    static void main(String[] args) throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        IO.println("Building db (data folder: %s) ...".formatted(path));
        try (var vulcanoDB = createVulcanoDB()) {

            IO.println("Generating documents...");
            var samples = generateSamples();

            IO.println("Storing %d documents...".formatted(samples.size()));
            samples.stream().parallel().forEach(vulcanoDB::add);

            IO.println("Generating embedding for the query...");
            var query = "Clever horror movie with great acting";
            var queryVector = Embedding.of(query);

            IO.println("Non-indexed search:");
            long t0 = System.currentTimeMillis();
            var nonIndexedQuery = Query.builder().isSimilarTo(queryVector, "nonIndexedVector").build();
            vulcanoDB
                    .search(nonIndexedQuery, 10)
                    .getDocuments()
                    .forEach(HnswIndexPerformance::showResult);

            long bruteTime = System.currentTimeMillis() - t0;
            IO.println("Non-indexedd time: %d ms".formatted(bruteTime));


            IO.println("Index search: ");
            t0 = System.currentTimeMillis();
            var indexedQuery = Query.builder().isSimilarTo(queryVector, "indexedVector").build();
            vulcanoDB
                    .search(indexedQuery, 10)
                    .getDocuments()
                    .forEach(HnswIndexPerformance::showResult);
            var indexTime = System.currentTimeMillis() - t0;
            IO.println("Index time: %d ms.".formatted(indexTime));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        IO.println("Removing data folder %s".formatted(path));
        FileUtils.deleteRecursively(path.toFile());
    }

    private static void showResult(ResultDocument result) {
        IO.println("[%.2f] %s -> %s".formatted(
                result.score(),
                result.document().id(),
                result.document().field("originalText").get().value()));
    }

    private static List<Document> generateSamples() throws URISyntaxException, IOException {
        var loader = Thread.currentThread().getContextClassLoader();
        var url = loader.getResource("rotten-tomatoes-reviews.txt");
        Path path = Paths.get(url.toURI());
        try (var lines = Files.lines(path)) {
            return lines
                    .parallel()
                    .map(txt -> {
                        var embedding = Embedding.of(txt);
                        return Document.builder()
                                .withStringField("originalText", txt)
                                .withVectorField("nonIndexedVector", embedding)
                                .withVectorField("indexedVector", embedding)
                                .build();
                    })
                    .toList();
        }
    }


    private static VulcanoDb createVulcanoDB() {
        var properties = new Properties();
        properties.setProperty(ConfigProperties.PROPERTY_PATH, path.toString());
        var hnswConfig = HnswConfig.builder()
                .withEfConstruction(500) // max recall
                .withEfSearch(500) // max recall
                .build();
        var axon = AxonDataStore
                .builder()
                .withDocumentWriter(new DefaultDocumentPersister(new TypedProperties(properties)))
                .withVectorIndex("indexedVector", hnswConfig)
                .build();

        return VulcanoDb
                .builder()
                .withDataStore(axon)
                .build();
    }
}
