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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.structLayout;

/**
 * Defines the binary layout for Tucana storage structures using FFM API.
 *
 * @author nacho
 */
public final class Layout {

    private Layout() {}

    /**
     * Page size (4 KB as per paper)
     */
    public static final long PAGE_SIZE = 4096;

    /**
     * Superblock Layout (stored at the beginning of each segment)
     * Contains references to the latest consistent state.
     */
    public static final MemoryLayout SUPERBLOCK = structLayout(
            ValueLayout.JAVA_LONG.withName("magic"),      // Magic number for validation
            ValueLayout.JAVA_LONG.withName("epoch"),      // Current consistent epoch
            ValueLayout.JAVA_LONG.withName("rootOffset"), // Offset to the root node of the tree
            ValueLayout.JAVA_LONG.withName("logTail"),    // Offset to the tail of the append-only log
            ValueLayout.JAVA_LONG.withName("historyOffset"), // Offset to the epoch history log
            ValueLayout.JAVA_LONG.withName("checksum")    // CRC for superblock integrity
    );

    public static final VarHandle SB_MAGIC = SUPERBLOCK.varHandle(groupElement("magic"));
    public static final VarHandle SB_EPOCH = SUPERBLOCK.varHandle(groupElement("epoch"));
    public static final VarHandle SB_ROOT = SUPERBLOCK.varHandle(groupElement("rootOffset"));
    public static final VarHandle SB_LOG_TAIL = SUPERBLOCK.varHandle(groupElement("logTail"));
    public static final VarHandle SB_HISTORY = SUPERBLOCK.varHandle(groupElement("historyOffset"));
    public static final VarHandle SB_CHECKSUM = SUPERBLOCK.varHandle(groupElement("checksum"));

    /**
     * Epoch History Entry Layout
     * Tracks the root offset for a specific epoch.
     */
    public static final MemoryLayout HISTORY_ENTRY = structLayout(
            ValueLayout.JAVA_LONG.withName("epoch"),
            ValueLayout.JAVA_LONG.withName("rootOffset")
    );

    /**
     * Internal Node Header Layout
     */
    public static final MemoryLayout INTERNAL_NODE_HEADER = structLayout(
            ValueLayout.JAVA_INT.withName("nodeType"),    // Type (Internal vs Leaf)
            ValueLayout.JAVA_INT.withName("numPivots"),   // Number of pivot keys
            ValueLayout.JAVA_LONG.withName("epoch")       // Node's version (epoch)
    );

    public static final VarHandle IN_TYPE = INTERNAL_NODE_HEADER.varHandle(groupElement("nodeType"));
    public static final VarHandle IN_NUM_PIVOTS = INTERNAL_NODE_HEADER.varHandle(groupElement("numPivots"));
    public static final VarHandle IN_EPOCH = INTERNAL_NODE_HEADER.varHandle(groupElement("epoch"));

    /**
     * Internal Node Pivot Entry
     * Pointers to keys and child nodes.
     */
    public static final MemoryLayout PIVOT_ENTRY = structLayout(
            ValueLayout.JAVA_LONG.withName("keyOffset"),  // Offset to variable-size key in buffer
            ValueLayout.JAVA_INT.withName("keySize"),     // Size of the key
            ValueLayout.JAVA_LONG.withName("childOffset") // Offset to child node (Internal or Leaf)
    );

    /**
     * Leaf Node Header Layout
     */
    public static final MemoryLayout LEAF_NODE_HEADER = structLayout(
            ValueLayout.JAVA_INT.withName("nodeType"),    // Type
            ValueLayout.JAVA_INT.withName("numEntries"),  // Number of KV pairs
            ValueLayout.JAVA_LONG.withName("epoch")       // Node's version
    );

    public static final VarHandle LN_TYPE = LEAF_NODE_HEADER.varHandle(groupElement("nodeType"));
    public static final VarHandle LN_NUM_ENTRIES = LEAF_NODE_HEADER.varHandle(groupElement("numEntries"));
    public static final VarHandle LN_EPOCH = LEAF_NODE_HEADER.varHandle(groupElement("epoch"));

    /**
     * Leaf Node Entry
     * Includes optimizations: Prefix and Hash.
     */
    public static final MemoryLayout LEAF_ENTRY = structLayout(
            ValueLayout.JAVA_INT.withName("keyPrefix"),   // First 4 bytes of key
            ValueLayout.JAVA_INT.withName("keyHash"),     // Jenkins hash of the key
            ValueLayout.JAVA_LONG.withName("dataOffset")  // Offset into DataLog for (key, value)
    );

    /**
     * DataLog Entry Header
     */
    public static final MemoryLayout LOG_ENTRY_HEADER = structLayout(
            ValueLayout.JAVA_INT.withName("keySize"),
            ValueLayout.JAVA_INT.withName("valueSize"),
            ValueLayout.JAVA_BYTE.withName("valueType")   // String, Int, FloatArray, etc.
    );

    // Node types
    public static final int TYPE_INTERNAL = 1;
    public static final int TYPE_LEAF = 2;

    // Constants for binary search optimization
    public static final int PREFIX_SIZE = 4;
}
