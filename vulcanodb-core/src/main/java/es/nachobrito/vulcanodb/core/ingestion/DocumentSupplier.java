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

package es.nachobrito.vulcanodb.core.ingestion;

import es.nachobrito.vulcanodb.core.document.Document;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author nacho
 */

public interface DocumentSupplier {

    default void initialize() {
        //implement this method for download files, or prepare resources.
        // This will be invoked asynchronously by the DocumentIngestor.
    }

    Stream<Supplier<Document>> getDocuments();
}
