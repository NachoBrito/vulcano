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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer;

/**
 * Abstraction for memory-mapped buffers using the FFM API.
 * Provides low-level access to the underlying storage via offset-based methods.
 */
public interface TucanaBuffer {

    /**
     * Reads a byte at the given offset.
     *
     * @param offset the offset from which to read
     * @return the byte value
     */
    byte getByte(long offset);

    /**
     * Writes a byte at the given offset.
     *
     * @param offset the offset at which to write
     * @param value  the byte value to write
     */
    void putByte(long offset, byte value);

    /**
     * Reads an int at the given offset.
     *
     * @param offset the offset from which to read
     * @return the int value
     */
    int getInt(long offset);

    /**
     * Writes an int at the given offset.
     *
     * @param offset the offset at which to write
     * @param value  the int value to write
     */
    void putInt(long offset, int value);

    /**
     * Reads a long at the given offset.
     *
     * @param offset the offset from which to read
     * @return the long value
     */
    long getLong(long offset);

    /**
     * Writes a long at the given offset.
     *
     * @param offset the offset at which to write
     * @param value  the long value to write
     */
    void putLong(long offset, long value);

    /**
     * Returns a {@link java.nio.ByteBuffer} view of a slice of this buffer.
     *
     * @param offset the starting offset of the slice
     * @param length the length of the slice
     * @return a ByteBuffer view of the slice
     */
    java.nio.ByteBuffer getBytes(long offset, int length);

    /**
     * Writes the content of the given {@link ByteBuffer} to this buffer at the specified offset.
     *
     * @param offset the offset at which to start writing
     * @param buffer the buffer containing the data to write
     */
    void putBuffer(long offset, java.nio.ByteBuffer buffer);

    /**
     * Returns the base offset of this buffer within the global storage.
     *
     * @return the base offset
     */
    long offset();

    /**
     * Forces changes to be written to the underlying storage.
     */
    void force();
}
