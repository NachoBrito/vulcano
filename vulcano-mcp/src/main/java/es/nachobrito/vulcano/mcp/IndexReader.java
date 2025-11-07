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

package es.nachobrito.vulcano.mcp;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import es.nachobrito.vulcanodb.core.domain.model.VulcanoDb;
import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static es.nachobrito.vulcano.mcp.VectorHelper.toDoubles;

/**
 * @author nacho
 */
@Singleton
public class IndexReader {
    private static final double SCORE_THRESHOLD = .5;
    private final String indexFile;
    private VulcanoDb vectorDb;
    private EmbeddingModel embeddingModel;

    public IndexReader(@Value("${vulcano.mcp.index-file}") String indexFile) {
        this.indexFile = indexFile;
    }

    private void init() {
        if (vectorDb != null) {
            return;
        }
        vectorDb = VulcanoDb.builder().build();

        var indexPath = Path.of(indexFile);
        if (!indexPath.toFile().isFile()) {
            throw new RuntimeException("Index File not found: %s".formatted(indexFile));
        }
        try (var is = Files.newInputStream(indexPath)) {
            var yaml = new Yaml();
            Map<String, Map<String, Map<String, Map<String, String>>>> entries = yaml.load(is);
            entries
                    .computeIfAbsent("vulcano", (key) -> Collections.emptyMap())
                    .computeIfAbsent("artifacts", (String key) -> Collections.emptyMap())
                    .forEach(this::indexEntry);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void indexEntry(String s, Map<String, String> o) {
        var path = verifyPath(o.get("path"));
        var description = o.get("description");
        var embedding = toDoubles(getEmbeddingModel().embed(description).content().vector());
        var document = Document.builder()
                .withStringField("path", path)
                .withStringField("description", description)
                .withVectorField("embedding", embedding)
                .build();
        vectorDb.add(document);
    }

    private String verifyPath(String path) {
        try {
            var parent = Path.of(this.indexFile).getParent().toString();
            var pathObject = Path.of(parent, path).toRealPath();
            if (!pathObject.toFile().isFile()) {
                throw new RuntimeException("File not found: %s".formatted(pathObject));
            }
            return pathObject.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            embeddingModel = new BgeSmallEnV15EmbeddingModel();
        }
        return embeddingModel;
    }

    public List<RelevantPath> getRelevantFiles(String query) {
        init();
        var embeddingModel = getEmbeddingModel();
        var vector = toDoubles(embeddingModel.embed(query).content().vector());
        var vectorQuery = Query.builder().isSimilarTo(vector, "embedding").build();
        return vectorDb
                .search(vectorQuery)
                .getDocuments()
                .stream()
                .filter(it -> it.score() >= SCORE_THRESHOLD)
                .map(result -> {
                    var path = (String) result.document().field("path").orElseThrow().value();
                    return new RelevantPath(Path.of(path), result.score());
                })
                .toList();
    }

}
