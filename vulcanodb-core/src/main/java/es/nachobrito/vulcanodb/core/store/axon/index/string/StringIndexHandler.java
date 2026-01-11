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

package es.nachobrito.vulcanodb.core.store.axon.index.string;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.Field;
import es.nachobrito.vulcanodb.core.document.StringFieldValue;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.store.axon.index.IndexMatch;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LeafNode;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public class StringIndexHandler implements IndexHandler<String> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String fieldName;
    private final InvertedIndex invertedIndex;

    public StringIndexHandler(String fieldName, Path basePath) {
        this.fieldName = fieldName;
        this.invertedIndex = new InvertedIndex(basePath);
    }

    @Override
    public void index(Long internalId, Document document) {
        Optional<Field<?, ?>> mayBeField = document.field(fieldName);
        if (mayBeField.isEmpty()) {
            return;
        }
        Field<?, ?> field = mayBeField.get();
        if (!field.type().equals(StringFieldValue.class)) {
            return;
        }

        String value = (String) field.value();
        invertedIndex.add(value, internalId);
    }

    @Override
    public List<IndexMatch> search(LeafNode<String> query, int maxResults) {
        Operation operation = query.operator();
        String value = query.value();

        if (operation == Operation.STRING_EQUALS) {
            String idsStr = invertedIndex.getIds(value);
            return parseIds(idsStr, maxResults);
        }

        Stream<String> termStream;
        if (operation == Operation.STRING_STARTS_WITH) {
            termStream = invertedIndex.terms().filter(term -> term.startsWith(value));
        } else if (operation == Operation.STRING_ENDS_WITH) {
            termStream = invertedIndex.terms().filter(term -> term.endsWith(value));
        } else if (operation == Operation.STRING_CONTAINS) {
            termStream = invertedIndex.terms().filter(term -> term.contains(value));
        } else {
            return List.of();
        }

        return termStream
                .flatMap(term -> Arrays.stream(invertedIndex.getIds(term).split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(maxResults)
                .map(id -> new IndexMatch(Long.parseLong(id), 1.0f))
                .toList();
    }

    private List<IndexMatch> parseIds(String idsStr, int maxResults) {
        if (idsStr.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(idsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(maxResults)
                .map(id -> new IndexMatch(Long.parseLong(id), 1.0f))
                .toList();
    }

    @Override
    public void close() throws Exception {
        invertedIndex.close();
    }
}
