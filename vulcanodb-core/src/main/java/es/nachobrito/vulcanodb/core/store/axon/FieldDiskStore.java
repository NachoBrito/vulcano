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
import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nacho
 */
class FieldDiskStore implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<FieldIdentity<?>, KeyValueStore> stores = new ConcurrentHashMap<>();
    private final Path dataFolder;

    public FieldDiskStore(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * Writes the field value to the corresponding store.
     *
     * @param documentId the document id
     * @param field      the document field
     * @param <V>        the value type
     * @param <T>        the FieldValueType
     * @return the result of this operation
     */
    public <V, T extends FieldValueType<V>> FieldWriteResult writeField(String documentId, Field<V, T> field) {
        var fieldIdentity = FieldIdentity.of(field);
        var store = stores.computeIfAbsent(fieldIdentity, this::createValueStore);

        if (log.isDebugEnabled()) {
            log.debug("[{}] Writing {}:{} ({})", Thread.currentThread(), documentId, field.key(), field.type());
        }
        try {
            switch (field.value()) {
                case String stringValue -> store.putString(documentId, stringValue);
                case Integer intValue -> store.putInt(documentId, intValue);
                case float[] vectorValue -> store.putFloatArray(documentId, vectorValue);
                case float[][] matrixValue -> store.putFloatMatrix(documentId, matrixValue);
                default -> throw new IllegalArgumentException(
                        "Unknown data type: %s for field '%s'".formatted(
                                field.value().getClass(), field.key()));
            }
            return FieldWriteResult.success(field.key());
        } catch (Throwable throwable) {
            return FieldWriteResult.error(field.key(), throwable);
        }
    }


    private KeyValueStore createValueStore(FieldIdentity<?> fieldIdentity) {
        try {
            return new KeyValueStore(getDestinationPath(fieldIdentity));
        } catch (IOException e) {
            throw new AxonDataStoreException(e);
        }
    }

    private Path getDestinationPath(FieldIdentity<?> identity) {
        var folder = FileUtils.toLegalFileName(identity.fieldName());
        var parent = dataFolder.resolve(folder, identity.type().getSimpleName());
        if (!parent.toFile().isDirectory() && !parent.toFile().mkdirs()) {
            throw new AxonDataStoreException("Cannot create folder: " + parent);
        }
        return parent;
    }

    @Override
    public void close() throws Exception {
        for (var store : stores.values()) {
            store.close();
        }
    }

    /**
     * Reads all the fields described by the shape object, associated to the document id.
     *
     * @param shape the document shape
     * @return the field values
     */
    public Map<String, Object> readFields(DocumentShape shape) {
        Map<String, Object> values = new ConcurrentHashMap<>();
        var id = shape.getDocumentId().toString();
        shape
                .getFields()
                .entrySet()
                .parallelStream()
                .forEach(entry -> readToMap(entry.getKey(), entry.getValue(), values, id));

        return values;
    }

    private void readToMap(String fieldName, Class<? extends FieldValueType<?>> type, Map<String, Object> values, String id) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Reading to map {}:{} ({})", Thread.currentThread(), id, fieldName, type);
        }
        var identity = new FieldIdentity<>(fieldName, type);
        var store = stores.computeIfAbsent(identity, this::createValueStore);
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

    /**
     * Reads the provided field from the document with the corresponding id
     *
     * @param documentId the document id
     * @param fieldName  the field name
     * @param valueType  the value type
     * @param <T>        the type of the field value
     * @return the value, if it is found
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> readDocumentField(String documentId, String fieldName, Class<? extends FieldValueType<T>> valueType) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Reading {}:{} ({})", Thread.currentThread(), documentId, fieldName, valueType);
        }
        var identity = new FieldIdentity<>(fieldName, valueType);
        var store = stores.computeIfAbsent(identity, this::createValueStore);
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

    /**
     * Removes all the field values associated to the document represented by the received shape.
     *
     * @param shape the document shape
     */
    public void removeFields(DocumentShape shape) {
        var id = shape.getDocumentId().toString();
        shape
                .getFields()
                .entrySet()
                .parallelStream()
                .forEach(entry -> removeField(entry.getKey(), entry.getValue(), id));
    }

    private void removeField(String fieldName, Class<? extends FieldValueType<?>> type, String documentId) {
        var identity = new FieldIdentity<>(fieldName, type);
        var store = stores.computeIfAbsent(identity, this::createValueStore);
        store.remove(documentId);
    }
}
