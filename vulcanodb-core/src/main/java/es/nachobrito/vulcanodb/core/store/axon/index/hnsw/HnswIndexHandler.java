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

package es.nachobrito.vulcanodb.core.store.axon.index.hnsw;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.Field;
import es.nachobrito.vulcanodb.core.document.VectorFieldValue;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexMatch;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LeafNode;
import es.nachobrito.vulcanodb.core.util.PagedLongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author nacho
 */
public class HnswIndexHandler implements IndexHandler<float[]> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HnswIndex index;
    private final String fieldName;
    private final PagedLongArray documentIdMap;

    public HnswIndexHandler(String fieldName, HnswConfig hnswConfig, Path basePath) {
        this.fieldName = fieldName;
        this.index = new HnswIndex(hnswConfig, basePath);
        this.documentIdMap = new PagedLongArray(4096, basePath.resolve("id_map"));
    }


    @Override
    public void index(Long internalId, Document document) {
        var mayBeField = document.field(fieldName);
        if (mayBeField.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring document {}, it does not contain field {}", document.id(), fieldName);
            }
            return;
        }
        var field = mayBeField.get();
        if (!field.type().equals(VectorFieldValue.class)) {
            throw new IllegalArgumentException(
                    "Field %s in document %s is of invalid type %s"
                            .formatted(fieldName, document.id(), field.type().getName()));
        }

        @SuppressWarnings("unchecked")
        var newId = index.insert(((Field<float[], VectorFieldValue>) field).value());
        documentIdMap.set(newId, internalId);
        if (log.isDebugEnabled()) {
            log.debug("Indexed document {}, with internal id {} -> new id: {}", document.id(), internalId, newId);
        }
    }

    @Override
    public List<IndexMatch> search(LeafNode<float[]> query, int maxResults) {
        float[] vector = (float[]) query.value();
        var hits = index.search(vector, maxResults);
        if (log.isDebugEnabled()) {
            log.debug("Search returned {} hits: {}", hits.size(), hits.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }

        return hits
                .stream()
                .map(hit -> new IndexMatch(documentIdMap.get(hit.vectorId()), hit.similarity()))
                .sorted()
                .toList();
    }

    @Override
    public void close() throws Exception {
        index.close();
        documentIdMap.close();
    }

    @Override
    public long offHeapBytes() {
        return index.offHeapBytes() + documentIdMap.offHeapBytes();
    }
}
