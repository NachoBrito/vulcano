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

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.document.DocumentShape;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.DocumentDiskStore;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.DocumentWriteResult;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.FieldWriteResult;
import es.nachobrito.vulcanodb.core.infrastructure.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.util.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static es.nachobrito.vulcanodb.core.domain.model.store.axon.ConfigProperties.PROPERTY_PATH;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * @author nacho
 */
public class DefaultDocumentDiskStore implements DocumentDiskStore {

    private static final String DEFAULT_PATH = System.getenv("HOME") + "/.vulcanoDb";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FieldDiskStore fieldDiskStore;
    private final KeyValueStore dictionary;

    public DefaultDocumentDiskStore(TypedProperties config) {
        var path = Path.of(config.getString(PROPERTY_PATH, DEFAULT_PATH));
        this(path);
    }

    public DefaultDocumentDiskStore() {
        var path = Path.of(DEFAULT_PATH);
        this(path);
    }

    public DefaultDocumentDiskStore(Path dataFolder) {
        this.fieldDiskStore = new FieldDiskStore(dataFolder);
        try {
            this.dictionary = new KeyValueStore(dataFolder.resolve("dictionary"));
        } catch (IOException e) {
            throw new AxonDataStoreException(e);
        }
    }


    /**
     * Returns the result of writing the Document as a Future that will contain a {@link DocumentWriteResult} when the
     * operation completes.
     *
     * @param document the document to write
     * @return a Future containing the result.
     */
    @Override
    public CompletableFuture<DocumentWriteResult> write(Document document) {
        if (logger.isDebugEnabled()) {
            logger.debug("Writing document {}", document.id());
        }
        var executor = ExecutorProvider.defaultExecutor();

        //1. Save all the field values
        var fieldCallables = document
                .getfieldsStream()
                .map(field -> supplyAsync(() -> fieldDiskStore.writeField(document.id(), field), executor))
                .toArray(CompletableFuture[]::new);

        //2. Save document shape
        AtomicReference<Throwable> error = new AtomicReference<>();
        //noinspection unchecked
        return allOf(fieldCallables)
                .exceptionally(t -> {
                    error.set(t);
                    return null;
                })
                .thenApplyAsync(_ -> commitDocument(document, fieldCallables, error.get()), executor);
    }

    @Override
    public Optional<Document> read(DocumentId documentId) {
        var shapeString = this.dictionary.getString(documentId.toString());
        if (shapeString.isEmpty()) {
            return Optional.empty();
        }
        var shape = DocumentShape.from(shapeString.get());
        var values = fieldDiskStore.readFields(documentId, shape);
        var document = Document
                .builder()
                .withId(documentId)
                .with(values)
                .build();

        return Optional.of(document);
    }

    private DocumentWriteResult commitDocument(
            Document document, CompletableFuture<FieldWriteResult>[] fieldCallables, Throwable error) {
        if (logger.isDebugEnabled()) {
            logger.debug("Writing shape of document {}", document.id());
        }
        if (error != null) {
            return DocumentWriteResult.ofError(error);
        }

        var fieldResults = Arrays.stream(fieldCallables).map(CompletableFuture::resultNow).toList();
        this.dictionary.putString(document.id().toString(), document.getShape().toString());
        return DocumentWriteResult.ofFieldResults(fieldResults);
    }

    @Override
    public void close() throws Exception {
        fieldDiskStore.close();
    }
}
