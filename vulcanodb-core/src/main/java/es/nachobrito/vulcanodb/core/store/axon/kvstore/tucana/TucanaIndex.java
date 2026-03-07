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

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Interface for the Bε-tree index used in Tucana.
 */
public interface TucanaIndex {
    /**
     * Inserts or updates a key with its data offset in the tree.
     *
     * @param key    the key to insert or update
     * @param offset the data offset associated with the key
     */
    void upsert(ByteBuffer key, long offset);

    /**
     * Deletes a key from the tree.
     *
     * @param key the key to delete
     */
    void delete(ByteBuffer key);

    /**
     * Searches for a key in the tree and returns its data offset.
     *
     * @param key the key to search for
     * @return an {@link OptionalLong} containing the offset if found, or empty otherwise
     */
    OptionalLong get(ByteBuffer key);

    /**
     * Searches for a key in the tree at a specific epoch and returns its data offset.
     *
     * @param key   the key to search for
     * @param epoch the epoch at which to perform the search
     * @return an {@link OptionalLong} containing the offset if found, or empty otherwise
     */
    OptionalLong getAtEpoch(ByteBuffer key, long epoch);

    /**
     * Returns the current root offset of the tree.
     *
     * @return the offset of the root node
     */
    long rootOffset();

    /**
     * Returns a stream of all valid data offsets currently indexed.
     *
     * @return a stream of offsets
     */
    Stream<Long> allOffsets();
}
