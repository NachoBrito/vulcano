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

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.document.DocumentId;
import es.nachobrito.vulcanodb.core.document.DocumentShape;
import es.nachobrito.vulcanodb.core.document.FieldValueType;
import es.nachobrito.vulcanodb.core.store.axon.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.store.axon.error.AxonDataStoreException;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * @author nacho
 */
public final class DefaultDocumentPersister implements DocumentPersister {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FieldDiskStore fieldDiskStore;
    private final KeyValueStore dictionary;

    public DefaultDocumentPersister(Path dataFolder) {
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

        //1. Save all the field values (with commit=false for batching)
        var fieldCallables = document
                .getfieldsStream()
                .map(field -> supplyAsync(() -> fieldDiskStore.writeField(document.id().toString(), field, false), executor))
                .toArray(CompletableFuture[]::new);

        //2. Save document shape and commit stores
        AtomicReference<Throwable> error = new AtomicReference<>();
        //noinspection unchecked
        return allOf(fieldCallables)
                .exceptionally(t -> {
                    error.set(t);
                    return null;
                })
                .thenApplyAsync(_ -> {
                    if (error.get() == null) {
                        fieldDiskStore.commitAll();
                    }
                    return commitDocument(document, fieldCallables, error.get());
                }, executor);
    }

    private DocumentWriteResult commitDocument(
            Document document, CompletableFuture<FieldWriteResult>[] fieldCallables, Throwable error) {
        var stringId = document.id().toString();
        if (logger.isDebugEnabled()) {
            logger.debug("Writing shape of document {}", stringId);
        }
        if (error != null) {
            return DocumentWriteResult.ofError(error);
        }

        var fieldResults = Arrays.stream(fieldCallables).map(CompletableFuture::resultNow).toList();
        var internalId = this.dictionary.putString(
                stringId, document.getShape().toString()
        );
        return DocumentWriteResult.ofFieldResults(internalId, fieldResults);
    }

    @Override
    public Optional<Document> read(DocumentId documentId) {
        var shapeString = this.dictionary.getString(documentId.toString());
        if (shapeString.isEmpty()) {
            return Optional.empty();
        }
        var shape = DocumentShape.from(shapeString.get());
        var values = fieldDiskStore.readFields(shape);
        var document = Document
                .builder()
                .withId(documentId)
                .with(values)
                .build();

        return Optional.of(document);
    }

    @Override
    public Stream<Long> internalIds() {
        return dictionary.getOffsetStream();
    }

    @Override
    public <T> Optional<T> readDocumentField(long internalId, String fieldName, Class<? extends FieldValueType<T>> valueType) {
        var shape = DocumentShape.from(dictionary.getStringAt(internalId));
        return fieldDiskStore.readDocumentField(shape.getDocumentId().toString(), fieldName, valueType);
    }

    @Override
    public void remove(DocumentId documentId) {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing document {}", documentId);
        }
        var shapeString = this.dictionary.getString(documentId.toString());
        if (shapeString.isEmpty()) {
            return;
        }
        var shape = DocumentShape.from(shapeString.get());
        fieldDiskStore.removeFields(shape);
        this.dictionary.remove(documentId.toString());
    }

    @Override
    public Optional<Document> read(long internalId) {
        String shapeString;
        try {
            shapeString = dictionary.getStringAt(internalId);
        } catch (Throwable throwable) {
            logger.error("Couldn't read document by its internal id: {}", internalId, throwable);
            return Optional.empty();
        }
        var shape = DocumentShape.from(shapeString);
        var values = fieldDiskStore.readFields(shape);
        return Optional.of(Document
                .builder()
                .withId(shape.getDocumentId())
                .with(values)
                .build());
    }


    @Override
    public long getOffHeapBytes() {
        return fieldDiskStore.offHeapBytes() + dictionary.offHeapBytes();
    }

    @Override
    public void close() throws Exception {
        fieldDiskStore.close();
        dictionary.close();
    }
}
