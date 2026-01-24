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

import java.io.IOException;
import java.util.List;

/**
 * Interface for the Write-Ahead Log manager.
 */
public interface WalManager extends AutoCloseable {

    /**
     * Records an intention to add a document.
     *
     * @param document the document to be added
     * @return a unique transaction ID for this operation
     * @throws IOException if the log cannot be written
     */
    long recordAdd(Document document) throws IOException;

    /**
     * Records an intention to remove a document.
     *
     * @param documentId the ID of the document to be removed
     * @return a unique transaction ID for this operation
     * @throws IOException if the log cannot be written
     */
    long recordRemove(String documentId) throws IOException;

    /**
     * Marks a transaction as committed in the log.
     *
     * @param txId the transaction ID
     * @throws IOException if the log cannot be written
     */
    void commit(long txId) throws IOException;

    /**
     * Returns all entries that have not been committed.
     *
     * @return a list of uncommitted entries
     * @throws IOException if the log cannot be read
     */
    List<WalEntry> readUncommitted() throws IOException;

    /**
     * Truncates the log up to the last committed transaction, if safe to do so.
     *
     * @throws IOException if the log cannot be modified
     */
    void checkpoint() throws IOException;

    long offHeapBytes();
}
