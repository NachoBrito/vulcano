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

package es.nachobrito.vulcanodb.core.domain.model.store;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public class InMemoryDataStore extends AbstractDataStore {
    private final ConcurrentHashMap<DocumentId, Document> documents = new ConcurrentHashMap<>();

    @Override
    public void add(Document document) {
        this.documents.put(document.id(), document);
    }

    @Override
    protected Stream<Document> getDocumentStream() {
        return this.documents.values().stream().parallel();
    }
}
