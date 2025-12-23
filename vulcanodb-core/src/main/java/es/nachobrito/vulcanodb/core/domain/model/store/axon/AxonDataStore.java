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

package es.nachobrito.vulcanodb.core.domain.model.store.axon;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;
import es.nachobrito.vulcanodb.core.domain.model.store.DataStore;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.index.HnswIndexHandler;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.index.IndexHandler;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.write.DocumentWriter;
import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.DefaultDocumentWriter;
import es.nachobrito.vulcanodb.core.util.TypedProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The Axon data store provides support for:
 * <ul>
 *     <li>write persistence via {@link DocumentWriter} implementations</li>
 *     <li>HNSW indexing for vector fields</li>
 * </ul>
 *
 * @author nacho
 */
public class AxonDataStore implements DataStore {

    private final Map<String, IndexHandler<?>> indexes;
    private final DocumentWriter documentWriter;

    private AxonDataStore(Map<String, IndexHandler<?>> indexes, DocumentWriter documentWriter) {
        this.indexes = indexes;
        this.documentWriter = documentWriter;
    }

    @Override
    public void add(Document document) {
        documentWriter.write(document);
    }

    @Override
    public Result search(Query query) {
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() throws Exception {
        documentWriter.close();
        var errors = new HashMap<IndexHandler<?>, Exception>();
        for (IndexHandler<?> indexHandler : indexes.values()) {
            try {
                indexHandler.close();
            } catch (Exception exception) {
                errors.put(indexHandler, exception);
            }
        }
        if (!errors.isEmpty()) {
            throw new AxonDataStoreCloseException("Some Index Handlers could not  be closed", errors);
        }
    }


    public static class Builder {
        private final Map<String, IndexHandler<?>> indexes = new HashMap<>();
        private final Properties config = new Properties();
        private DocumentWriter documentWriter;

        public AxonDataStore build() {
            var documentWriter = this.documentWriter != null ?
                    this.documentWriter :
                    new DefaultDocumentWriter(new TypedProperties(config));

            return new AxonDataStore(indexes, documentWriter);
        }

        public Builder withVectorIndex(String fieldName) {
            this.indexes.put(fieldName, new HnswIndexHandler(fieldName));
            return this;
        }

        public Builder withDocumentWriter(DocumentWriter documentWriter) {
            this.documentWriter = documentWriter;
            return this;
        }
    }
}
