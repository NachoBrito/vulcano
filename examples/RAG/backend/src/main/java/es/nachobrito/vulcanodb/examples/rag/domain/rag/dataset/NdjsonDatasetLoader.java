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

package es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public abstract class NdjsonDatasetLoader<T> implements DatasetLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NdjsonDatasetLoader.class);

    protected final ObjectMapper objectMapper;
    protected final ResourceResolver resourceResolver;
    protected final String resourcePath;
    protected final Class<T> entryType;

    private int maxDocuments = Integer.MAX_VALUE;

    protected NdjsonDatasetLoader(ObjectMapper objectMapper, ResourceResolver resourceResolver, String resourcePath, Class<T> entryType) {
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
        this.resourcePath = resourcePath;
        this.entryType = entryType;
    }

    @Override
    public Stream<DatasetEntry> getDocumentUrls() {
        Optional<InputStream> inputStreamOptional = resourceResolver.getResourceAsStream(resourcePath);
        if (inputStreamOptional.isEmpty()) {
            LOG.error(getMissingFileErrorMessage());
            return Stream.empty();
        }

        InputStream inputStream = inputStreamOptional.get();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        return reader.lines()
                .filter(line -> !line.isBlank())
                .limit(maxDocuments)
                .map(line -> {
                    try {
                        T entry = objectMapper.readValue(line, entryType);
                        return mapToDatasetEntry(entry);
                    } catch (IOException e) {
                        throw new RuntimeException("Error parsing line in " + resourcePath, e);
                    }
                })
                .onClose(() -> {
                    try {
                        reader.close();
                        inputStream.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                });
    }

    protected void setMaxDocuments(int maxDocuments) {
        this.maxDocuments = maxDocuments;
    }

    protected abstract DatasetEntry mapToDatasetEntry(T entry);

    protected abstract String getMissingFileErrorMessage();
}
