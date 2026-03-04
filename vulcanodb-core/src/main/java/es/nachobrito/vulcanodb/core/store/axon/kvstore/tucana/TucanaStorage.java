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

/**
 * Manages the physical storage of Tucana, including segment allocation and CoW.
 */
public interface TucanaStorage extends AutoCloseable {
    /**
     * Allocates a new buffer of the specified size.
     */
    TucanaBuffer allocate(long size);

    /**
     * Returns the buffer at the specified offset.
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
     */
    long currentEpoch();

    /**
     * Returns the root offset for the given epoch.
     */
    Optional<Long> getRootOffset(long epoch);

    /**
     * Sets the root offset for the current epoch.
     */
    void setRootOffset(long offset);

    /**
     * Reads a {@link ByteBuffer} from the storage at the specified offset.
     */
    ByteBuffer read(long offset);

    /**
     * Writes the given data to the storage and returns the offset.
     */
    long write(byte[] data);
}
