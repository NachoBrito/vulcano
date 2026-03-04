/*
 *    Copyright 2026 Nacho Brito
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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Interface for the Bε-tree index used in Tucana.
 */
public interface TucanaIndex {
    /**
     * Inserts or updates a key with its data offset in the tree.
     */
    void upsert(byte[] key, long offset);

    /**
     * Deletes a key from the tree.
     */
    void delete(byte[] key);

    /**
     * Searches for a key in the tree and returns its data offset.
     */
    OptionalLong get(byte[] key);

    /**
     * Searches for a key in the tree at a specific epoch and returns its data offset.
     */
    OptionalLong getAtEpoch(byte[] key, long epoch);

    /**
     * Returns the current root offset of the tree.
     */
    long rootOffset();

    /**
     * Returns a stream of all valid data offsets currently indexed.
     */
    Stream<Long> allOffsets();
}
