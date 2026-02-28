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

/**
 * Abstraction for memory-mapped buffers using the FFM API.
 * Provides low-level access to the underlying storage via offset-based methods.
 */
public interface TucanaBuffer {

    /**
     * Reads a byte at the given offset.
     */
    byte getByte(long offset);

    /**
     * Writes a byte at the given offset.
     */
    void putByte(long offset, byte value);

    /**
     * Reads an int at the given offset.
     */
    int getInt(long offset);

    /**
     * Writes an int at the given offset.
     */
    void putInt(long offset, int value);

    /**
     * Reads a long at the given offset.
     */
    long getLong(long offset);

    /**
     * Writes a long at the given offset.
     */
    void putLong(long offset, long value);

    /**
     * Forces changes to be written to the underlying storage.
     */
    void force();
}
