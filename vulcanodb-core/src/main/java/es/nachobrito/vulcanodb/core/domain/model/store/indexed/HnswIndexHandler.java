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

package es.nachobrito.vulcanodb.core.domain.model.store.indexed;

import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.store.indexed.hnsw.HnswConfig;
import es.nachobrito.vulcanodb.core.domain.model.store.indexed.hnsw.HnswIndex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nacho
 */
public class HnswIndexHandler implements IndexHandler<float[]> {
    private final HnswIndex index;

    private final Map<Long, DocumentId> documentIdMap = new HashMap<>();

    public HnswIndexHandler() {
        var config = HnswConfig.builder().build();
        this.index = new HnswIndex(config);
    }


    @Override
    public void index(DocumentId documentId, float[] value) {
        var newId = index.insert(value);
        documentIdMap.put(newId, documentId);
    }

    @Override
    public List<IndexMatch> search(float[] query, int maxResults) {
        var hits = index.search(query, maxResults);
        return hits
                .stream()
                .map(hit -> new IndexMatch(documentIdMap.get(hit.vectorId()), hit.similarity()))
                .toList();
    }
}
