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

package es.nachobrito.vulcanodb.core.store.axon.wal;

import es.nachobrito.vulcanodb.core.document.Document;
import java.util.Optional;

/**
 * Represents a single entry in the Write-Ahead Log.
 */
public record WalEntry(
    long txId,
    Type type,
    Optional<Document> document,
    Optional<String> documentId,
    boolean committed
) {
    public enum Type {
        ADD, REMOVE
    }

    public static WalEntry add(long txId, Document document) {
        return new WalEntry(txId, Type.ADD, Optional.of(document), Optional.empty(), false);
    }

    public static WalEntry remove(long txId, String documentId) {
        return new WalEntry(txId, Type.REMOVE, Optional.empty(), Optional.of(documentId), false);
    }
}
