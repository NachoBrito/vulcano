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

package es.nachobrito.vulcanodb.core.store.axon.index;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.document.Field;
import es.nachobrito.vulcanodb.core.document.VectorFieldValue;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.store.axon.index.hnsw.HnswIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nacho
 */
public class HnswIndexHandler implements IndexHandler<float[]> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HnswIndex index;
    private final String fieldName;

    private final Map<Long, DocumentId> documentIdMap = new HashMap<>();

    public HnswIndexHandler(String fieldName) {
        this.fieldName = fieldName;
        var config = HnswConfig.builder().build();
        this.index = new HnswIndex(config);
    }


    @Override
    public void index(Document document) {
        var mayBeField = document.field(fieldName);
        if (mayBeField.isEmpty()) {
            log.debug("Ignoring document {}, it does not contain field {}", document.id(), fieldName);
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
        documentIdMap.put(newId, document.id());
    }

    @Override
    public List<IndexMatch> search(float[] query, int maxResults) {
        var hits = index.search(query, maxResults);
        return hits
                .stream()
                .map(hit -> new IndexMatch(documentIdMap.get(hit.vectorId()), hit.similarity()))
                .toList();
    }

    @Override
    public void close() throws Exception {
        //nothing to do
    }
}
