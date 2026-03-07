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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.index;

import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer.TucanaBuffer;
import java.nio.ByteBuffer;

/**
 * Utility for managing the binary layout of a Bε-tree node within a {@link TucanaBuffer}.
 * <p>
 * This class provides constants and static methods to manipulate the raw bytes of a node
 * without creating intermediate objects, minimizing GC pressure.
 * <p>
 * Node Layout:
 * <ul>
 *   <li>[0-3]   Magic Number (0xBE77EE11 for Internal, 0xBE77EE22 for Leaf)</li>
 *   <li>[4-7]   Checksum (CRC32 for crash consistency)</li>
 *   <li>[8-11]  Number of pivots (Internal) or Entries (Leaf)</li>
 *   <li>[12-15] Message Buffer Offset (where new messages are appended)</li>
 *   <li>[16-23] Parent Offset (for traversal/re-linking in the CoW path)</li>
 *   <li>[24...] Type-specific payload (Pivots/Child Offsets or Sorted Entries)</li>
 * </ul>
 */
public final class BeTreeNodeLayout {

    /**
     * Magic number for internal (non-leaf) nodes.
     */
    public static final int MAGIC_INTERNAL = 0xBE77EE11;
    
    /**
     * Magic number for leaf nodes.
     */
    public static final int MAGIC_LEAF = 0xBE77EE22;

    /**
     * Offset where the magic number is stored.
     */
    public static final int OFFSET_MAGIC = 0;
    
    /**
     * Offset where the checksum is stored.
     */
    public static final int OFFSET_CHECKSUM = 4;
    
    /**
     * Offset where the count of pivots or entries is stored.
     */
    public static final int OFFSET_COUNT = 8;
    
    /**
     * Offset where the current end of the message buffer is stored.
     */
    public static final int OFFSET_BUFFER_START = 12;
    
    /**
     * Offset where the parent node's offset is stored.
     */
    public static final int OFFSET_PARENT = 16;
    
    /**
     * The total size of the node header in bytes.
     */
    public static final int HEADER_SIZE = 24;

    /**
     * Message type identifier for UPSERT operations.
     */
    public static final byte MSG_UPSERT = 1;
    
    /**
     * Message type identifier for DELETE operations.
     */
    public static final byte MSG_DELETE = 2;

    private BeTreeNodeLayout() {}

    /**
     * Checks if the given node buffer represents a leaf node.
     *
     * @param buffer the buffer containing the node data
     * @return true if it's a leaf node, false otherwise
     */
    public static boolean isLeaf(TucanaBuffer buffer) {
        return buffer.getInt(OFFSET_MAGIC) == MAGIC_LEAF;
    }

    /**
     * Retrieves the count of pivots or entries in the node.
     *
     * @param buffer the buffer containing the node data
     * @return the count
     */
    public static int getCount(TucanaBuffer buffer) {
        return buffer.getInt(OFFSET_COUNT);
    }

    /**
     * Sets the count of pivots or entries in the node.
     *
     * @param buffer the buffer containing the node data
     * @param count the count to set
     */
    public static void setCount(TucanaBuffer buffer, int count) {
        buffer.putInt(OFFSET_COUNT, count);
    }

    /**
     * Retrieves the current end offset of the message buffer.
     *
     * @param buffer the buffer containing the node data
     * @return the buffer offset
     */
    public static int getBufferOffset(TucanaBuffer buffer) {
        return buffer.getInt(OFFSET_BUFFER_START);
    }

    /**
     * Sets the current end offset of the message buffer.
     *
     * @param buffer the buffer containing the node data
     * @param offset the offset to set
     */
    public static void setBufferOffset(TucanaBuffer buffer, int offset) {
        buffer.putInt(OFFSET_BUFFER_START, offset);
    }

    /**
     * Compares two ByteBuffers lexicographically.
     *
     * @param b1 the first buffer
     * @param b2 the second buffer
     * @return a negative integer, zero, or a positive integer as the first buffer
     *         is less than, equal to, or greater than the second
     */
    public static int compare(ByteBuffer b1, ByteBuffer b2) {
        return b1.compareTo(b2);
    }
}
