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
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.util.FileUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
            var nonIndexedQuery = VulcanoDb.queryBuilder().isSimilarTo(queryVector, "nonIndexedVector").build();
            vulcanoDB
                    .search(nonIndexedQuery, 10)
                    .getDocuments()
                    .forEach(HnswIndexPerformance::showResult);

            long bruteTime = System.currentTimeMillis() - t0;
            IO.println("Non-indexed time: %d ms".formatted(bruteTime));


            IO.println("Indexed search: ");
            t0 = System.currentTimeMillis();
            var indexedQuery = VulcanoDb.queryBuilder().isSimilarTo(queryVector, "indexedVector").build();
            vulcanoDB
                    .search(indexedQuery, 10)
                    .getDocuments()
                    .forEach(HnswIndexPerformance::showResult);
            var indexTime = System.currentTimeMillis() - t0;
            IO.println("Indexed time: %d ms.".formatted(indexTime));
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
        var hnswConfig = HnswConfig.builder()
                .withEfConstruction(500) // max recall
                .withEfSearch(500) // max recall
                .build();
        var axon = AxonDataStore
                .builder()
                .withDataFolder(path)
                .withVectorIndex("indexedVector", hnswConfig)
                .build();

        return VulcanoDb
                .builder()
                .withDataStore(axon)
                .build();
    }
}
