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

package es.nachobrito.vulcanodb.core.store.axon;

import es.nachobrito.vulcanodb.core.document.*;
import es.nachobrito.vulcanodb.core.store.axon.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStoreProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public class TaskPerDocumentPersister implements DocumentPersister {
    private final KeyValueStoreProvider keyValueStoreProvider;
    private final KeyValueStore dictionary;

    public TaskPerDocumentPersister(KeyValueStoreProvider keyValueStoreProvider) {
        this.keyValueStoreProvider = keyValueStoreProvider;
        this.dictionary = keyValueStoreProvider.getKeyValueStore("document-dictionary");
    }

    @Override
    public CompletableFuture<DocumentWriteResult> write(Document document) {
        return CompletableFuture.supplyAsync(() -> this.internalWrite(document), ExecutorProvider.ingestionExecutor());
    }

    private DocumentWriteResult internalWrite(Document document) {
        //1. write shape, generate internal id
        var internalId = dictionary.putString(document.id().toString(), document.getShape().toString());

        //2. write fields
        var fieldResults = document
                .getfieldsStream()
                .map(field -> this.writeField(document.id(), field))
                .toList();

        return DocumentWriteResult.ofFieldResults(internalId, fieldResults);
    }

    private FieldWriteResult writeField(DocumentId documentId, Field<?, ?> field) {
        var identity = FieldIdentity.of(field);
        var store = keyValueStoreProvider.forField(identity);
        var stringId = documentId.toString();
        try {
            switch (field.value()) {
                case String stringValue -> store.putString(stringId, stringValue);
                case Integer intValue -> store.putInt(stringId, intValue);
                case float[] vectorValue -> store.putFloatArray(stringId, vectorValue);
                case float[][] matrixValue -> store.putFloatMatrix(stringId, matrixValue);
                default -> throw new IllegalArgumentException(
                        "Unknown data type: %s for field '%s'".formatted(
                                field.value().getClass(), field.key()));
            }
            return FieldWriteResult.success(field.key());
        } catch (Throwable throwable) {
            return FieldWriteResult.error(field.key(), throwable);
        }
    }


    @Override
    public Optional<Document> read(DocumentId documentId) {
        var maybeShapeString = this.dictionary.getString(documentId.toString());
        if (maybeShapeString.isEmpty()) {
            return Optional.empty();
        }
        var shape = DocumentShape
                .from(maybeShapeString.get());
        return internalRead(shape);

    }

    private Optional<Document> internalRead(DocumentShape shape) {
        var values = new HashMap<String, Object>();
        shape
                .getFields()
                .forEach((key, field) -> this.readFieldToMap(shape.getDocumentId(), key, field, values));

        var document = Document
                .builder()
                .withId(shape.getDocumentId())
                .with(values)
                .build();

        return Optional.of(document);
    }

    private void readFieldToMap(DocumentId documentId, String fieldName, Class<? extends FieldValueType<?>> type, Map<String, Object> values) {
        var identity = new FieldIdentity<>(fieldName, type);
        var store = keyValueStoreProvider.forField(identity);
        var id = documentId.toString();
        if (type.equals(IntegerFieldValue.class)) {
            store.getInt(id).ifPresent(integer -> values.put(fieldName, integer));
            return;
        }
        if (type.equals(MatrixFieldValue.class)) {
            store
                    .getFloatMatrix(id)
                    .ifPresent(value -> values.put(fieldName, value));
            return;
        }
        if (type.equals(StringFieldValue.class)) {
            store
                    .getString(id)
                    .ifPresent(value -> values.put(fieldName, value));
            return;
        }
        if (type.equals(VectorFieldValue.class)) {
            store
                    .getFloatArray(id)
                    .ifPresent(value -> values.put(fieldName, value));
            return;
        }
        throw new IllegalStateException("Unknown field type: " + type);
    }


    @Override
    public Optional<Document> read(long internalId) {
        var shapeString = this.dictionary.getStringAt(internalId);
        var shape = DocumentShape.from(shapeString);
        return internalRead(shape);
    }

    @Override
    public Stream<Long> internalIds() {
        return dictionary.getOffsetStream();
    }

    @Override
    public <T> Optional<T> readDocumentField(long internalId, String fieldName, Class<? extends FieldValueType<T>> valueType) {
        var shapeString = this.dictionary.getStringAt(internalId);
        var shape = DocumentShape.from(shapeString);
        var fieldIdentity = new FieldIdentity<>(fieldName, valueType);
        var store = keyValueStoreProvider.forField(fieldIdentity);
        var documentId = shape.getDocumentId().toString();
        if (valueType.equals(IntegerFieldValue.class)) {
            return (Optional<T>) store.getInt(documentId);

        }
        if (valueType.equals(MatrixFieldValue.class)) {
            return (Optional<T>) store.getFloatMatrix(documentId);
        }
        if (valueType.equals(StringFieldValue.class)) {
            return (Optional<T>) store.getString(documentId);
        }
        if (valueType.equals(VectorFieldValue.class)) {
            return (Optional<T>) store.getFloatArray(documentId);
        }
        throw new IllegalStateException("Unknown field type: " + valueType);
    }

    @Override
    public void remove(DocumentId documentId) {
        var shapeString = this.dictionary.getString(documentId.toString());
        if (shapeString.isEmpty()) {
            return;
        }
        var shape = DocumentShape.from(shapeString.get());
        shape
                .getFields()
                .forEach((fieldName, fieldType) -> this.removeField(fieldName, fieldType, documentId));
        this.dictionary.remove(documentId.toString());
    }

    private void removeField(String fieldName, Class<? extends FieldValueType<?>> fieldType, DocumentId documentId) {
        var identity = new FieldIdentity<>(fieldName, fieldType);
        var store = keyValueStoreProvider.forField(identity);
        var id = documentId.toString();
        store.remove(id);
    }

    @Override
    public long getOffHeapBytes() {
        return dictionary.offHeapBytes() + keyValueStoreProvider.totalOffHeapBytes();
    }

    @Override
    public void close() throws Exception {
        dictionary.close();
        keyValueStoreProvider.closeAll();
    }
}
