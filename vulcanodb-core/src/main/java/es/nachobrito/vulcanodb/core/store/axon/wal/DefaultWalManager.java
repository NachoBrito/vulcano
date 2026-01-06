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
import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of WalManager using KeyValueStore and binary serialization.
 */
public class DefaultWalManager implements WalManager {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final KeyValueStore kvStore;
    private final AtomicLong nextTxId = new AtomicLong(System.currentTimeMillis());

    public DefaultWalManager(Path path) throws IOException {
        this.kvStore = new KeyValueStore(path);
    }

    @Override
    public long recordAdd(Document document) throws IOException {
        long txId = nextTxId.incrementAndGet();
        WalEntry entry = WalEntry.add(txId, document);
        byte[] binary = WalSerializer.serialize(entry);
        kvStore.putBytes(String.valueOf(txId), binary);
        return txId;
    }

    @Override
    public long recordRemove(String documentId) throws IOException {
        long txId = nextTxId.incrementAndGet();
        WalEntry entry = WalEntry.remove(txId, documentId);
        byte[] binary = WalSerializer.serialize(entry);
        kvStore.putBytes(String.valueOf(txId), binary);
        return txId;
    }

    @Override
    public void commit(long txId) throws IOException {
        kvStore.remove(String.valueOf(txId));
    }

    @Override
    public List<WalEntry> readUncommitted() throws IOException {
        List<WalEntry> uncommitted = new ArrayList<>();
        kvStore.getOffsetStream().forEach(offset -> {
            try {
                byte[] binary = kvStore.getBytesAt(offset);
                uncommitted.add(WalSerializer.deserialize(binary));
            } catch (IOException e) {
                log.error("Failed to deserialize uncommitted WAL entry at offset {}", offset, e);
            }
        });
        return uncommitted;
    }

    @Override
    public void checkpoint() throws IOException {
        // KVStore already manages its own consistency
    }

    @Override
    public void close() throws Exception {
        kvStore.close();
    }
}
