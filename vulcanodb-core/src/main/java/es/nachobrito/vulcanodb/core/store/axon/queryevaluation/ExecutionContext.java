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

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.FieldValueType;
import es.nachobrito.vulcanodb.core.store.axon.DocumentPersister;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.field.IndexedField;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.field.ScannableField;

/**
 * @author nacho
 */
public class ExecutionContext {
    private final DocumentPersister documentPersister;

    public ExecutionContext(DocumentPersister documentPersister) {
        this.documentPersister = documentPersister;
    }


    public IndexedField getIndexedField(String fieldName) {
        return null;
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

    public Document loadDocument(long id) {
        return documentPersister.read(id);
    }
}
