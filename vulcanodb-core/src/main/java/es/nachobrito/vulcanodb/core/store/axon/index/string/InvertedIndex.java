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

package es.nachobrito.vulcanodb.core.store.axon.index.string;

import es.nachobrito.vulcanodb.core.store.axon.kvstore.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public final class InvertedIndex implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final KeyValueStore kvStore;

    public InvertedIndex(Path basePath) {
        try {
            this.kvStore = new KeyValueStore(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create InvertedIndex", e);
        }
    }

    public void add(String term, Long internalId) {
        String key = "term:" + term;
        String existingIds = kvStore.getString(key).orElse("");

        // We use a simple comma-separated string of IDs. 
        // We ensure no leading/trailing spaces are added.
        String newIds;
        if (existingIds.isEmpty()) {
            newIds = String.valueOf(internalId);
        } else {
            newIds = existingIds + "," + internalId;
        }
        if (log.isDebugEnabled()) {
            log.debug("Updating inverted index: {} -> {}", key, newIds);
        }
        kvStore.putString(key, newIds);
    }

    public String getIds(String term) {
        var ids = kvStore.getString("term:" + term);
        if (log.isDebugEnabled()) {
            log.debug("Found {} ids for {}: {}", ids.map(String::length).orElse(0), term, ids.orElse(""));
        }
        return ids.orElse("");
    }

    public Stream<String> terms() {
        return kvStore.getOffsetStream()
                .map(offset -> {
                    try {
                        String key = kvStore.getKeyAt(offset);
                        if (key.startsWith("term:")) {
                            return key.substring(5);
                        }
                    } catch (Exception e) {
                        // Ignore corrupt or non-matching entries
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct();
    }

    @Override
    public void close() throws Exception {
        kvStore.close();
    }

    public long offHeapBytes() {
        return kvStore.offHeapBytes();
    }
}
