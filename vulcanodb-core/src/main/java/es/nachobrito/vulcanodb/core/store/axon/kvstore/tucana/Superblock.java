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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32;

/**
 * Represents a Tucana Superblock.
 * <p>
 * The Superblock is the root of trust for the persistent storage. It enables atomic commits
 * and crash consistency by pointing to the current consistent state of the database.
 * <p>
 * Layout:
 * - Magic (8 bytes): "VULCANOD"
 * - Epoch (8 bytes): long
 * - Root Offset (8 bytes): long
 * - Allocator Offset (8 bytes): long
 * - Checksum (8 bytes): long (CRC32)
 */
class Superblock {
    /**
     * Magic number to identify the database file format ("VULCANOD").
     */
    static final long MAGIC = 0x56554C43414E4F44L; // "VULCANOD" in hex (8 bytes)

    /** Offset of the magic number field. */
    private static final long MAGIC_OFFSET = 0;
    /** Offset of the epoch field. */
    private static final long EPOCH_OFFSET = 8;
    /** Offset of the root offset field. */
    private static final long ROOT_OFFSET = 16;
    /** Offset of the allocator offset field. */
    private static final long ALLOCATOR_OFFSET = 24;
    /** Offset of the checksum field. */
    private static final long CHECKSUM_OFFSET = 32;

    /**
     * Total size of the Superblock in bytes.
     */
    static final long SIZE = 40;

    /** The underlying memory segment where the superblock is stored. */
    private final MemorySegment segment;

    /**
     * Creates a new Superblock view over the given memory segment.
     * @param segment the memory segment (must be at least {@link #SIZE} bytes).
     */
    Superblock(MemorySegment segment) {
        this.segment = segment;
    }

    /**
     * Returns the magic number stored in the superblock.
     * @return the magic number.
     */
    long magic() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, MAGIC_OFFSET);
    }

    /**
     * Sets the magic number to the standard {@link #MAGIC} value.
     */
    void setMagic() {
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, MAGIC_OFFSET, MAGIC);
    }

    /**
     * Returns the current epoch (version) of this superblock.
     * @return the epoch number.
     */
    long epoch() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, EPOCH_OFFSET);
    }

    /**
     * Sets the epoch (version) of this superblock.
     * @param epoch the new epoch number.
     */
    void setEpoch(long epoch) {
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, EPOCH_OFFSET, epoch);
    }

    /**
     * Returns the root offset of the B&epsilon;-tree for this epoch.
     * @return the root offset, or -1 if the tree is empty.
     */
    long rootOffset() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, ROOT_OFFSET);
    }

    /**
     * Sets the root offset of the B&epsilon;-tree for this epoch.
     * @param offset the new root offset.
     */
    void setRootOffset(long offset) {
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, ROOT_OFFSET, offset);
    }

    /**
     * Returns the allocator offset from which new data should be written in the next epoch.
     * @return the allocator offset.
     */
    long allocatorOffset() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, ALLOCATOR_OFFSET);
    }

    /**
     * Sets the allocator offset for this epoch.
     * @param offset the new allocator offset.
     */
    void setAllocatorOffset(long offset) {
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, ALLOCATOR_OFFSET, offset);
    }

    /**
     * Returns the checksum of the superblock fields.
     * @return the checksum value.
     */
    long checksum() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, CHECKSUM_OFFSET);
    }

    /**
     * Updates the checksum field by calculating the CRC32 of all other fields in the superblock.
     */
    void updateChecksum() {
        CRC32 crc = new CRC32();
        byte[] data = segment.asSlice(0, CHECKSUM_OFFSET).toArray(ValueLayout.JAVA_BYTE);
        crc.update(data);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, CHECKSUM_OFFSET, crc.getValue());
    }

    /**
     * Validates the superblock by checking the magic number and verifying the checksum.
     * @return true if the superblock is valid, false otherwise.
     */
    boolean isValid() {
        if (magic() != MAGIC) return false;
        CRC32 crc = new CRC32();
        byte[] data = segment.asSlice(0, 32).toArray(ValueLayout.JAVA_BYTE);
        crc.update(data);
        return checksum() == crc.getValue();
    }
}
