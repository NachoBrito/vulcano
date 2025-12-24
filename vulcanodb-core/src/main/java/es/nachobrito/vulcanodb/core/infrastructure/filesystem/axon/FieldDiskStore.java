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

package es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon;

import es.nachobrito.vulcanodb.core.domain.model.document.*;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.FieldWriteResult;
import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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

    public <V, T extends FieldValueType<V>> FieldWriteResult writeField(DocumentId documentId, Field<V, T> field) {
        var fieldIdentity = FieldIdentity.of(field);
        var store = stores.computeIfAbsent(fieldIdentity, this::createValueStore);

        if (log.isDebugEnabled()) {
            log.debug("Writing {}:{} ({})", documentId, field.key(), field.type());
        }
        try {
            switch (field.value()) {
                case String stringValue -> store.putString(documentId.toString(), stringValue);
                case Integer intValue -> store.putInt(documentId.toString(), intValue);
                case float[] vectorValue -> store.putFloatArray(documentId.toString(), vectorValue);
                case float[][] matrixValue -> store.putFloatMatrix(documentId.toString(), matrixValue);
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
     * @param documentId the document id
     * @param shape      the document shape
     * @return the field values
     */
    public Map<String, Object> readFields(DocumentId documentId, DocumentShape shape) {
        Map<String, Object> values = new HashMap<>();
        var key = documentId.toString();
        for (var entry : shape.getFields().entrySet()) {
            var type = entry.getValue();
            var identity = new FieldIdentity<>(entry.getKey(), entry.getValue());
            var store = stores.computeIfAbsent(identity, this::createValueStore);
            if (type.equals(IntegerFieldValue.class)) {
                values.put(entry.getKey(), store.getInt(key).orElseThrow());
                continue;
            }
            if (type.equals(MatrixFieldValue.class)) {
                values.put(entry.getKey(), store.getFloatMatrix(key).orElseThrow());
                continue;
            }
            if (type.equals(StringFieldValue.class)) {
                values.put(entry.getKey(), store.getString(key).orElseThrow());
                continue;
            }
            if (type.equals(VectorFieldValue.class)) {
                values.put(entry.getKey(), store.getFloatArray(key).orElseThrow());
                continue;
            }
            throw new IllegalStateException("Unknown field type: " + type);
        }
        return values;
    }
}
