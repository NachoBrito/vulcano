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

import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.document.Field;
import es.nachobrito.vulcanodb.core.domain.model.document.FieldValueType;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.write.FieldWriteResult;
import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.store.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import es.nachobrito.vulcanodb.core.util.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author nacho
 */
public class FieldWriter implements AutoCloseable {
    private static final String PROPERTY_PATH = "vulcanodb.axon.writer.path";

    private static final String DEFAULT_PATH = System.getenv("HOME") + "/.vulcanoDb";

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<FieldIdentity<?>, KeyValueStore> stores = new HashMap<>();
    private final Path dataFolder;

    public FieldWriter(TypedProperties config) {
        this.dataFolder = Path.of(config.getString(PROPERTY_PATH, DEFAULT_PATH));
    }

    @SuppressWarnings("unchecked")
    public <V, T extends FieldValueType<V>> Callable<FieldWriteResult> writeOperation(DocumentId documentId, Field<V, T> field) {
        var fieldIdentity = FieldIdentity.of(field);
        var store = stores.computeIfAbsent(fieldIdentity, this::createValueStore);
        return () -> {
            if (log.isDebugEnabled()) {
                log.debug("Writing {}:{} ({})", documentId, field.key(), field.type());
            }
            try {
                switch (field.value()) {
                    case String stringValue -> store.putString(documentId.toString(), stringValue);
                    case Integer intValue -> store.putInt(documentId.toString(), intValue);
                    case float[] vectorValue -> store.putFloatArray(documentId.toString(), vectorValue);
                    default -> throw new IllegalArgumentException(
                            "Unknown data type: %s for field '%s'".formatted(
                                    field.value().getClass(), field.key()));
                }
                return FieldWriteResult.success(field.key());
            } catch (Throwable throwable) {
                return FieldWriteResult.error(field.key(), throwable);
            }
        };
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

    }
}
