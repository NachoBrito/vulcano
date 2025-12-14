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

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;
import es.nachobrito.vulcanodb.core.domain.model.store.DataStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nacho
 */
public class IndexedDataStore implements DataStore {

    private final Map<String, IndexHandler<?>> indexes;

    private IndexedDataStore(Map<String, IndexHandler<?>> indexes) {
        this.indexes = indexes;
    }

    @Override
    public void add(Document document) {

    }

    @Override
    public Result search(Query query) {
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, IndexHandler<?>> indexes = new HashMap<>();

        public IndexedDataStore build() {
            return new IndexedDataStore(indexes);
        }

        public Builder withVectorIndex(String fieldName) {
            this.indexes.put(fieldName, new HnswIndexHandler(fieldName));
            return this;
        }
    }
}
