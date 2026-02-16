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

package es.nachobrito.vulcanodb.langchain4j;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentBuilder;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.ResultDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static es.nachobrito.vulcanodb.core.ingestion.DocumentSupplier.FIELD_EMBEDDING;
import static es.nachobrito.vulcanodb.core.ingestion.DocumentSupplier.FIELD_TEXT;

/**
 * @author nacho
 */
public class VulcanoDbEmbeddingStore implements EmbeddingStore<TextSegment> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String FIELD_METADATA_PREFIX = "metadata.";
    private static final String FIELD_EXTERNAL_ID = "extId";

    public static final String METADATA_VALUE_SEPARATOR = ":";
    private final VulcanoDb vulcanoDb;

    public VulcanoDbEmbeddingStore(VulcanoDb vulcanoDb) {
        this.vulcanoDb = vulcanoDb;
    }

    @Override
    public String add(Embedding embedding) {
        var document = getDocumentBuilder(null, embedding, null).build();
        vulcanoDb.add(document);
        return document.id().toString();
    }

    @Override
    public void add(String id, Embedding embedding) {
        var document = getDocumentBuilder(id, embedding, null).build();

        vulcanoDb.add(document);
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        var builder = getDocumentBuilder(null, embedding, embedded);

        var document = builder.build();
        vulcanoDb.add(document);

        return document.id().toString();
    }

    private DocumentBuilder getDocumentBuilder(String externalId, Embedding embedding, TextSegment embedded) {
        var builder = Document
                .builder()
                .withVectorField(FIELD_EMBEDDING, embedding.vector());

        var id = DocumentId.newRandomId();
        builder
                .withId(id)
                .withStringField(FIELD_EXTERNAL_ID, externalId != null ? externalId : id.toString());

        if (embedded != null) {
            builder.withStringField(FIELD_TEXT, embedded.text());
            embedded.metadata().toMap().forEach((key, value) -> {
                var fieldName = FIELD_METADATA_PREFIX + key;
                var fieldValue = value.getClass().getName() + METADATA_VALUE_SEPARATOR + value;
                builder.withStringField(fieldName, fieldValue);
            });
        }

        return builder;
    }

    @Override
    public List<String> addAll(List<Embedding> list) {
        return list.stream().map(this::add).toList();
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ids.forEach(this::remove);
    }

    @Override
    public void remove(String id) {
        var query = VulcanoDb
                .queryBuilder()
                .isEqual(id, FIELD_EXTERNAL_ID)
                .build();

        var result = vulcanoDb.search(query);
        if (result.isEmpty()) {
            return;
        }
        result
                .getDocuments()
                .stream().map(ResultDocument::document)
                .map(Document::id)
                .forEach(vulcanoDb::remove);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("Collections are not of the same size");
        }

        for (int i = 0; i < ids.size(); i++) {
            var document = getDocumentBuilder(ids.get(i), embeddings.get(i), embedded.get(i)).build();
            vulcanoDb.add(document);
        }
    }


    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("Collections are not of the same size");
        }
        var ids = new ArrayList<String>(embedded.size());
        for (int i = 0; i < embeddings.size(); i++) {
            var document = getDocumentBuilder(null, embeddings.get(i), embedded.get(i)).build();
            vulcanoDb.add(document);
            ids.add(document.id().toString());
        }
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        var query = toVulcanoDbQuery(embeddingSearchRequest);
        //bring twice as many results as requested, as they will be filtered by min score
        var results = vulcanoDb.search(query, 2 * embeddingSearchRequest.maxResults());

        return new EmbeddingSearchResult<>(results
                .getDocuments()
                .stream()
                .filter(it -> it.score() >= embeddingSearchRequest.minScore())
                .limit(embeddingSearchRequest.maxResults())
                .map(this::toEmbeddingMatch)
                .toList());
    }

    private Query toVulcanoDbQuery(EmbeddingSearchRequest searchRequest) {
        var queryBuilder = VulcanoDb.queryBuilder();
        var queryVector = searchRequest.queryEmbedding().vector();
        queryBuilder.isSimilarTo(queryVector, FIELD_EMBEDDING);
        return queryBuilder.build();
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(ResultDocument resultDocument) {
        var document = resultDocument.document();

        String text = null;
        float[] vector = null;
        String id = document.id().toString();
        var metadata = new Metadata();
        if (logger.isDebugEnabled()) {
            logger.debug("Building embedding match for document {}", document.id().toString());
        }
        for (Map.Entry<String, Object> entry : document.toMap().entrySet()) {
            if (logger.isDebugEnabled()) {
                logger.debug("    {} -> {}", entry.getKey(), entry.getValue());
            }
            switch (entry.getKey()) {
                case FIELD_TEXT:
                    text = (String) entry.getValue();
                    break;
                case FIELD_EMBEDDING:
                    vector = (float[]) entry.getValue();
                    break;
                case FIELD_EXTERNAL_ID:
                    id = (String) entry.getValue();
                default:
                    if (entry.getKey().startsWith(FIELD_METADATA_PREFIX)) {
                        addMetadataValue(entry.getKey(), (String) entry.getValue(), metadata);
                    }
            }
        }

        TextSegment textSegment = text != null ? TextSegment.from(text, metadata) : null;
        Embedding embedding = vector != null ? Embedding.from(vector) : null;
        double score = resultDocument.score();

        var match = new EmbeddingMatch<>(score, id, embedding, textSegment);
        if (logger.isDebugEnabled()) {
            logger.debug("Result: {}", match);
        }
        return match;
    }

    private void addMetadataValue(String key, String value, Metadata metadata) {
        var metadataKey = key.substring(FIELD_METADATA_PREFIX.length());
        var valueParts = value.split(METADATA_VALUE_SEPARATOR);
        /*
         * Metadata fields added through one of the add methods will be prefixed with their type,
         * but there can be fields in the document not following this pattern, if they were inserted directly into the
         * database.
         */
        if (valueParts.length != 2) {
            metadata.put(metadataKey, value);
            return;
        }

        var valueType = valueParts[0];
        switch (valueType) {
            case "java.lang.Integer":
                metadata.put(metadataKey, Integer.parseInt(valueParts[1]));
                break;
            case "java.lang.Float":
                metadata.put(metadataKey, Float.parseFloat(valueParts[1]));
                break;
            case "java.lang.Double":
                metadata.put(metadataKey, Double.parseDouble(valueParts[1]));
                break;
            case "java.lang.Long":
                metadata.put(metadataKey, Long.parseLong(valueParts[1]));
                break;
            case "java.util.UUID":
                metadata.put(metadataKey, UUID.fromString(valueParts[1]));
                break;
            default:
                metadata.put(metadataKey, valueParts[1]);
                break;

        }

    }

}
