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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.FieldValueType;
import es.nachobrito.vulcanodb.core.store.axon.DocumentPersister;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.field.IndexedField;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.field.ScannableField;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LeafNode;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author nacho
 */
public class ExecutionContext {
    private final DocumentPersister documentPersister;
    private final Map<String, IndexHandler<?>> indexHandlers;

    private final Map<Long, Collection<Float>> scores = new ConcurrentHashMap<>();

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "DocumentPersister is not ext. mutable, it is ok if indexHandlers is modified outside.")
    public ExecutionContext(DocumentPersister documentPersister, Map<String, IndexHandler<?>> indexHandlers) {
        this.documentPersister = documentPersister;
        this.indexHandlers = indexHandlers;
    }

    public <T> IndexedField<T> getIndexedField(LeafNode<T> node) {
        //noinspection unchecked
        return (IndexedField<T>) getIndexedField(node.fieldName(), node.value().getClass());
    }

    public <T> IndexedField<T> getIndexedField(String fieldName, Class<T> type) {
        return (LeafNode<T> query) -> {
            var docIds = new Roaring64DocIdSet();
            //noinspection unchecked
            var handler = (IndexHandler<T>) indexHandlers.get(fieldName);
            if (handler == null) {
                return docIds;
            }
            handler
                    .search(query)
                    .forEach(it -> {
                        docIds.add(it.internalId());
                        saveVectorScore(it.internalId(), it.score());
                    });
            return docIds;
        };
    }


    public <T> ScannableField<T> getScannableField(String fieldName, Class<? extends FieldValueType<T>> valueType) {
        return (long offset) -> documentPersister.readDocumentField(offset, fieldName, valueType).orElse(null);
    }

    public DocIdSet getAllDocs() {
        var set = new Roaring64DocIdSet();
        documentPersister
                .internalIds()
                .forEach(set::add);
        return set;
    }

    public Optional<Document> loadDocument(long id) {
        return documentPersister.read(id);
    }

    /**
     * Stores a score value for the given document
     *
     * @param internalId the internal id of the document
     * @param score      the score value
     */
    public void saveVectorScore(long internalId, float score) {
        scores.computeIfAbsent(internalId, _ -> new ConcurrentLinkedQueue<>()).add(score);
    }

    /**
     * Returns the average score for a document
     *
     * @param internalId the internal id of the document
     * @return the average value of all the stored scores
     */
    public float getAverageScore(long internalId) {
        if (!scores.containsKey(internalId)) {
            return 0.0f;
        }
        return (float) scores
                .get(internalId)
                .stream()
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }
}
