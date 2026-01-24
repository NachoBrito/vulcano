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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of WalManager using a high-performance append-only log.
 */
public class DefaultWalManager implements WalManager {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final WalLog walLog;
    private final AtomicLong nextTxId = new AtomicLong(System.currentTimeMillis());

    public DefaultWalManager(Path path) throws IOException {
        this.walLog = new WalLog(path);
    }

    @Override
    public long recordAdd(Document document) throws IOException {
        long txId = nextTxId.incrementAndGet();
        WalEntry entry = WalEntry.add(txId, document);
        byte[] binary = WalSerializer.serialize(entry);
        walLog.append(txId, binary);
        return txId;
    }

    @Override
    public long recordRemove(String documentId) throws IOException {
        long txId = nextTxId.incrementAndGet();
        WalEntry entry = WalEntry.remove(txId, documentId);
        byte[] binary = WalSerializer.serialize(entry);
        walLog.append(txId, binary);
        return txId;
    }

    @Override
    public void commit(long txId) throws IOException {
        walLog.markCommitted(txId);
    }

    @Override
    public List<WalEntry> readUncommitted() throws IOException {
        return walLog.uncommittedStream()
                .map(payload -> {
                    try {
                        return WalSerializer.deserialize(payload);
                    } catch (IOException e) {
                        log.error("Failed to deserialize uncommitted WAL entry", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public void checkpoint() throws IOException {
        // High-performance WAL manages its own consistency
    }

    @Override
    public void close() throws Exception {
        walLog.close();
    }

    @Override
    public long offHeapBytes() {
        return walLog.offHeapBytes();
    }
}
