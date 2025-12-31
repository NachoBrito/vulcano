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
import es.nachobrito.vulcanodb.core.util.TypedProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public class VulcanoCli {

    private static final String EXIT = "exit";
    private static final String LOAD = "load";
    private static final int MAX_RESULTS = 10;

    private static final String PATTERN = "[VulcanoCli] %s";

    static void main(String[] args) throws Exception {
        Path vulcanoDataPath = getVulcanoDataPath(args);
        try (var vulcanoDb = buildVulcanoDB(vulcanoDataPath)) {
            if (!vulcanoDataPath.toFile().isDirectory()) {
                loadData(vulcanoDb);
            }
            IO.println(PATTERN.formatted("Enter your query, I'll find relevant documents within the provided folder."));
            IO.println(PATTERN.formatted("You can also use the following commands:"));
            IO.println(PATTERN.formatted("- load -> to load documents from a folder."));
            IO.println(PATTERN.formatted("- exit -> to close VulcanoDB."));
            String input = null;
            while (!EXIT.equals(input)) {
                input = IO.readln();
                processInput(input, vulcanoDb);
            }

            IO.println(PATTERN.formatted("Thanks for testing VulcanoDB!"));
        }

    }


    private static Path getVulcanoDataPath(String[] args) throws IOException {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        return Files.createTempDirectory("vulcanodb-test");
    }

    private static VulcanoDb buildVulcanoDB(Path path) throws IOException {
        IO.println((PATTERN.formatted("Creating VulcanoDB instance witn data folder: " + path.toAbsolutePath())));

        var properties = new Properties();
        properties.setProperty(ConfigProperties.PROPERTY_PATH, path.toString());

        var axon = AxonDataStore
                .builder()
                .withVectorIndex("embedding")
                .withDocumentWriter(new DefaultDocumentPersister(new TypedProperties(properties)))
                .build();
        return VulcanoDb
                .builder()
                .withDataStore(axon)
                .build();
    }

    private static void loadData(VulcanoDb vulcanoDb) throws IOException {
        var path = askPath();
        try (Stream<Path> paths = Files.walk(path)) {
            var futures = paths
                    .map(it -> CompletableFuture.runAsync(() -> indexFile(it, vulcanoDb)))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
            IO.println(PATTERN.formatted("Folder content successfully indexed. You can start asking now."));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static void indexFile(Path file, VulcanoDb vulcanoDb) {
        if (!file.toFile().isFile()) {
            IO.println(PATTERN.formatted("Skipping non-file:  " + file.toAbsolutePath()));
            return;
        }
        IO.println(PATTERN.formatted("Indexing " + file.toAbsolutePath()));

        try {
            var text = Files.readString(file);
            if (text.isBlank()) {
                IO.println(PATTERN.formatted("Skipping empty file:  " + file.toAbsolutePath()));
                return;
            }
            var embedding = Embedding.of(text);
            var document = Document.builder()
                    .withVectorField("embedding", embedding)
                    .withStringField("path", file.toAbsolutePath().toString())
                    .build();
            vulcanoDb.add(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static Path askPath() {
        while (true) {
            var pathString = IO.readln(PATTERN.formatted("Enter the folder that contains the files to index: "));
            var path = Path.of(pathString);
            if (path.toFile().isDirectory()) {
                return path;
            }
            System.err.printf((PATTERN) + "%n", "That is not a valid path, please try again.").flush();
        }

    }

    private static void processInput(String input, VulcanoDb vulcanoDb) throws IOException {

        switch (input) {
            case LOAD:
                loadData(vulcanoDb);
            default:
                processQuery(input, vulcanoDb);
        }

    }

    private static void processQuery(String queryText, VulcanoDb vulcanoDb) {
        var t0 = System.currentTimeMillis();
        var embedding = Embedding.of(queryText);
        var embeddingTime = System.currentTimeMillis() - t0;

        var query = Query.builder().isSimilarTo(embedding, "embedding").build();

        t0 = System.currentTimeMillis();
        var results = vulcanoDb.search(query, MAX_RESULTS);
        var queryTime = System.currentTimeMillis() - t0;

        var message = results.isEmpty() ?
                "No results found for '%s'".formatted(queryText) :
                "Top %d results:".formatted(results.getDocuments().size());
        IO.println(PATTERN.formatted(message));
        results
                .getDocuments()
                .stream()
                .map(VulcanoCli::toResultString)
                .forEach(IO::println);
        IO.println("---");
        IO.println(PATTERN.formatted("Embedding time: %d ms. Query time: %d ms.".formatted(embeddingTime, queryTime)));
    }

    private static String toResultString(ResultDocument result) {
        var document = result.document();
        var path = document
                .field("path")
                .map(it -> it.content().value())
                .map(Object::toString)
                .orElse("???");

        return PATTERN.formatted("%.2f -> %s".formatted(result.score(), path));
    }
}
