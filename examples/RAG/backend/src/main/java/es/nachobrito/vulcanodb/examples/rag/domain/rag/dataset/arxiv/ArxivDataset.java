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

package es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.arxiv;

import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.DatasetEntry;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.NdjsonDatasetLoader;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author nacho
 */
@Singleton
@Named("ARXIV")
public class ArxivDataset extends NdjsonDatasetLoader<ArxivEntry> {

    public ArxivDataset(
            ObjectMapper objectMapper,
            ResourceResolver resourceResolver,
            @Property(name = "dataset.arxiv")
            String resourcePath) {
        super(objectMapper, resourceResolver, resourcePath, ArxivEntry.class);
        setMaxDocuments(100);
    }


    @Override
    protected DatasetEntry mapToDatasetEntry(ArxivEntry entry) {
        try {
            var url = "https://arxiv.org/pdf/" + entry.id();
            return new DatasetEntry(URI.create(url).toURL(), buildMetadata(entry));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getMissingFileErrorMessage() {
        return """
                ArXiv dataset file not found: %s
                Please download 'arxiv-metadata-oai-snapshot.json' from Kaggle:
                https://www.kaggle.com/datasets/Cornell-University/arxiv
                Then place it in: src/main/resources/%s
                """.formatted(resourcePath, resourcePath);
    }

    private Map<String, String> buildMetadata(ArxivEntry entry) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("id", entry.id());
        metadata.put("submitter", entry.submitter());
        metadata.put("authors", entry.authors());
        metadata.put("title", entry.title());
        metadata.put("comments", entry.comments());
        metadata.put("journalRef", entry.journalRef());
        metadata.put("doi", entry.doi());
        metadata.put("reportNo", entry.reportNo());
        metadata.put("categories", entry.categories());
        metadata.put("license", entry.license());
        metadata.put("abstract", entry.abstractText());
        metadata.put("updateDate", entry.updateDate());

        if (entry.versions() != null) {
            String versions = entry.versions().stream()
                    .map(v -> v.version() + ":" + v.created())
                    .collect(Collectors.joining(","));
            metadata.put("versions", versions);
        }

        if (entry.authorsParsed() != null) {
            String authorsParsed = entry.authorsParsed().stream()
                    .map(author -> String.join(" ", author).trim())
                    .collect(Collectors.joining(","));
            metadata.put("authorsParsed", authorsParsed);
        }

        return metadata;
    }
}
