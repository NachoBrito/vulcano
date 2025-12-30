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
    private static final String PATTERN = "[VulcanoCli] %s";

    static void main(String[] args) throws Exception {
        Path vulcanoDataPath = getPath(args);
        try (var vulcanoDb = buildVulcanoDB(vulcanoDataPath)) {
            if (dataLoadRequired(vulcanoDataPath)) {
                loadData(vulcanoDb);
            }

            IO.println(PATTERN.formatted("Enter your query, I'll find relevant documents within the provided folder."));
            String input = null;
            while (!EXIT.equals(input)) {
                input = IO.readln();
                IO.println(processInput(input, vulcanoDb));
            }

            IO.println(PATTERN.formatted("Thanks for testing VulcanoDB!"));
        }

    }

    private static boolean dataLoadRequired(Path dataPath) {
        if (!dataPath.toFile().isDirectory()) {
            return true;
        }
        var answer = IO.readln(PATTERN.formatted("Do you want to index new content? (y/n): "));
        return answer.toLowerCase().startsWith("y");
    }

    private static Path getPath(String[] args) throws IOException {
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

    private static String processInput(String input, VulcanoDb vulcanoDb) {

        var embedding = Embedding.of(input);
        var query = Query.builder().isSimilarTo(embedding, "embedding").build();
        var results = vulcanoDb.search(query);

        StringBuilder sb = new StringBuilder("Results for the query: %d".formatted(results.getDocuments().size())).append("\n");
        results
                .getDocuments()
                .forEach(doc -> sb
                        .append("%.2f -> %s".formatted(doc.score(), doc.document().field("path")))
                        .append("\n")
                );
        sb.append("\n---\n");
        return PATTERN.formatted(sb.toString());
    }
}
