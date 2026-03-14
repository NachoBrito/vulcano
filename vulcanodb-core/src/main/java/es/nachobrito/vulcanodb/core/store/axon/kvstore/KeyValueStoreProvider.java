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

package es.nachobrito.vulcanodb.core.store.axon.kvstore;

import es.nachobrito.vulcanodb.core.store.axon.FieldIdentity;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.TucanaIndex;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.TucanaKeyValueStore;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.index.TucanaBeTree;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.AxonTucanaStorage;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.TucanaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nacho
 */
public final class KeyValueStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(KeyValueStoreProvider.class);
    private final KeyValueStore store;
    private static final Map<String, KeyValueStore> stores = new HashMap<>();

    public KeyValueStoreProvider(Path dataFolder) {
        if (!dataFolder.toFile().isDirectory()) {
            throw new IllegalArgumentException(String.format("Data folder %s is not a directory", dataFolder));
        }
        store = buildKeyValueStore(dataFolder);
    }

    private KeyValueStore buildKeyValueStore(Path dataFolder) {
        Path storagePath = dataFolder.resolve("vulcano.db");
        TucanaStorage storage = new AxonTucanaStorage(storagePath);
        TucanaIndex index = new TucanaBeTree(storage);

        return new TucanaKeyValueStore(index, storage);
    }

    public KeyValueStore getKeyValueStore(String prefix) {
        return stores.computeIfAbsent(prefix, _ -> new PrefixedKeyValueStore(this.store, prefix));
    }

    public KeyValueStore forField(FieldIdentity<?> fieldIdentity) {
        var prefix = fieldIdentity.fieldName() + "/" + fieldIdentity.type().getName();
        return getKeyValueStore(prefix);
    }

    public void closeAll() {
        stores.values().forEach(this::closeStore);
    }

    private void closeStore(KeyValueStore keyValueStore) {
        try {
            keyValueStore.close();
        } catch (Exception e) {
            LOG.warn("Error while closing store {}", keyValueStore, e);
        }
    }

    public long totalOffHeapBytes() {
        return stores.values().stream().map(KeyValueStore::offHeapBytes).reduce(0L, Long::sum);
    }
}
