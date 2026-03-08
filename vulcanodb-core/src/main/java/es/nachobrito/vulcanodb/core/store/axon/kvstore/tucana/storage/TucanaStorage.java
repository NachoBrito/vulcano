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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage;

import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer.TucanaBuffer;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Manages the physical storage of Tucana, including segment allocation and CoW.
 */
public interface TucanaStorage extends AutoCloseable {
    /**
     * Allocates a new buffer of the specified size.
     *
     * @param size the size of the buffer to allocate
     * @return the allocated {@link TucanaBuffer}
     */
    TucanaBuffer allocate(long size);

    /**
     * Returns the buffer at the specified offset.
     *
     * @param offset the start offset of the buffer
     * @param size   the size of the buffer
     * @return a {@link TucanaBuffer} view at the specified offset
     */
    TucanaBuffer getBuffer(long offset, long size);

    /**
     * Commits the current state to the underlying storage (atomic swap of superblocks).
     */
    void commit();

    /**
     * Rolls back to the last committed state.
     */
    void rollback();

    /**
     * Returns the current epoch (version) of the storage.
     *
     * @return the current epoch number
     */
    long currentEpoch();

    /**
     * Returns the root offset for the given epoch.
     *
     * @param epoch the epoch for which to retrieve the root offset
     * @return an {@link Optional} containing the root offset if it exists for the given epoch
     */
    Optional<Long> getRootOffset(long epoch);

    /**
     * Sets the root offset for the current epoch.
     *
     * @param offset the new root offset
     */
    void setRootOffset(long offset);

    /**
     * Reads a {@link ByteBuffer} from the storage at the specified offset.
     *
     * @param offset the offset from which to read
     * @return a {@link ByteBuffer} containing the data read
     */
    ByteBuffer read(long offset);

    /**
     * Writes the given data to the storage and returns the offset.
     *
     * @param data the data to write
     * @return the offset where the data was written
     */
    long write(ByteBuffer data);
}
