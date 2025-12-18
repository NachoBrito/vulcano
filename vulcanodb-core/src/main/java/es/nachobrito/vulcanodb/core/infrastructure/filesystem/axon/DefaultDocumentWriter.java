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
import es.nachobrito.vulcanodb.core.domain.model.store.axon.filesystem.DocumentWriter;

import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

/**
 * @author nacho
 */
public class DefaultDocumentWriter implements DocumentWriter {
    private final Path dataFolder;
    private final FieldWriter fieldWriter;

    public DefaultDocumentWriter(Path dataFolder) {
        this.dataFolder = dataFolder;
        this.fieldWriter = new FieldWriter(dataFolder);
    }

    /**
     * Uses the common fork-join pool to write each field in the document asynchronously
     *
     * @param document the document to write
     */
    @Override
    public void write(Document document) {
        try {
            ForkJoinPool
                    .commonPool()
                    .invokeAll(
                            document
                                    .getfieldsStream()
                                    .map(it -> fieldWriter.writeOperation(document.id(), it))
                                    .toList()
                    );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
