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
import es.nachobrito.vulcanodb.core.domain.model.store.axon.write.DocumentWriteResult;
import es.nachobrito.vulcanodb.core.domain.model.store.axon.write.DocumentWriter;
import es.nachobrito.vulcanodb.core.infrastructure.concurrent.ExecutorProvider;
import es.nachobrito.vulcanodb.core.util.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * @author nacho
 */
public class DefaultDocumentWriter implements DocumentWriter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FieldWriter fieldWriter;

    public DefaultDocumentWriter(TypedProperties config) {
        this.fieldWriter = new FieldWriter(config);
    }

    public DefaultDocumentWriter() {
        this.fieldWriter = new FieldWriter(new TypedProperties());
    }


    /**
     * Returns the result of writing the Document as a Future that will contain a {@link DocumentWriteResult} when the
     * operation completes.
     *
     * @param document the document to write
     * @return a Future containing the result.
     */
    @Override
    public Future<DocumentWriteResult> write(Document document) {
        return ExecutorProvider
                .defaultExecutor()
                .submit(() -> writeDocumentAsync(document));
    }

    private DocumentWriteResult writeDocumentAsync(Document document) {
        if (logger.isDebugEnabled()) {
            logger.debug("Writing document {}", document.id());
        }
        try {
            var results = ExecutorProvider
                    .defaultExecutor()
                    .invokeAll(
                            document
                                    .getfieldsStream()
                                    .map(it -> fieldWriter.writeOperation(document.id(), it))
                                    .toList()
                    )
                    .stream()
                    .map(Future::resultNow)
                    .toList();
            return DocumentWriteResult.ofFieldResults(results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DocumentWriteResult.ofError(e);
        }
    }


    @Override
    public void close() throws Exception {
        fieldWriter.close();
    }
}
