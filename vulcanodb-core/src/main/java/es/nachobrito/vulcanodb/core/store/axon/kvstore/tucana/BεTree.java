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

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of the modified Bε-tree index for Tucana, supporting multi-segment storage and node splitting.
 *
 * @author nacho
 */
public final class BεTree {

    private final StorageManager storageManager;
    private final DataLog dataLog;
    private long rootOffset;

    private static final int MAX_LEAF_ENTRIES = 128; 
    private static final int MAX_INTERNAL_PIVOTS = 128;

    public BεTree(StorageManager storageManager, DataLog dataLog, long rootOffset) {
        this.storageManager = storageManager;
        this.dataLog = dataLog;
        this.rootOffset = rootOffset;

        if (this.rootOffset == 0) {
            try {
                this.rootOffset = createInitialLeaf();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private long createInitialLeaf() throws IOException {
        long offset = storageManager.allocatePage();
        MemorySegment page = getPage(offset);

        Layout.LN_TYPE.set(page, 0L, Layout.TYPE_LEAF);
        Layout.LN_NUM_ENTRIES.set(page, 0L, 0);
        Layout.LN_EPOCH.set(page, 0L, storageManager.currentEpoch());

        return offset;
    }

    private MemorySegment getPage(long globalOffset) {
        return storageManager.getSegmentForOffset(globalOffset).getPage(storageManager.localOffset(globalOffset));
    }

    public long search(String key) {
        return searchAtRoot(key, rootOffset);
    }

    public long searchAtRoot(String key, long targetRootOffset) {
        return search(targetRootOffset, key);
    }

    private long search(long nodeOffset, String key) {
        MemorySegment page = getPage(nodeOffset);
        int type = (int) Layout.LN_TYPE.get(page, 0L);

        if (type == Layout.TYPE_LEAF) {
            return searchLeaf(page, key);
        } else {
            long nextNodeOffset = findChild(page, key);
            return search(nextNodeOffset, key);
        }
    }

    private long searchLeaf(MemorySegment page, String key) {
        int numEntries = (int) Layout.LN_NUM_ENTRIES.get(page, 0L);
        int keyPrefix = getPrefix(key);
        int keyHash = getJenkinsHash(key);

        int low = 0;
        int high = numEntries - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long entryOffset = Layout.LEAF_NODE_HEADER.byteSize() + ((long) mid * Layout.LEAF_ENTRY.byteSize());
            int prefix = page.get(ValueLayout.JAVA_INT, entryOffset);

            if (prefix < keyPrefix) {
                low = mid + 1;
            } else if (prefix > keyPrefix) {
                high = mid - 1;
            } else {
                return linearSearchWithHash(page, mid, numEntries, keyPrefix, keyHash, key);
            }
        }
        return -1;
    }

    private long linearSearchWithHash(MemorySegment page, int startIndex, int numEntries, int keyPrefix, int keyHash, String key) {
        for (int i = startIndex; i < numEntries; i++) {
            long entryOffset = Layout.LEAF_NODE_HEADER.byteSize() + ((long) i * Layout.LEAF_ENTRY.byteSize());
            if (page.get(ValueLayout.JAVA_INT, entryOffset) != keyPrefix) break;

            if (page.get(ValueLayout.JAVA_INT, entryOffset + 4) == keyHash) {
                long dataOffset = page.get(ValueLayout.JAVA_LONG, entryOffset + 8);
                if (dataOffset != -1 && dataLog.readKey(dataOffset).equals(key)) {
                    return dataOffset;
                }
            }
        }
        for (int i = startIndex - 1; i >= 0; i--) {
            long entryOffset = Layout.LEAF_NODE_HEADER.byteSize() + ((long) i * Layout.LEAF_ENTRY.byteSize());
            if (page.get(ValueLayout.JAVA_INT, entryOffset) != keyPrefix) break;

            if (page.get(ValueLayout.JAVA_INT, entryOffset + 4) == keyHash) {
                long dataOffset = page.get(ValueLayout.JAVA_LONG, entryOffset + 8);
                if (dataOffset != -1 && dataLog.readKey(dataOffset).equals(key)) {
                    return dataOffset;
                }
            }
        }
        return -1;
    }

    private long findChild(MemorySegment page, String key) {
        int numPivots = (int) Layout.IN_NUM_PIVOTS.get(page, 0L);
        long pivotStart = Layout.INTERNAL_NODE_HEADER.byteSize();

        int low = 0;
        int high = numPivots - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long pivotOffset = pivotStart + ((long) mid * Layout.PIVOT_ENTRY.byteSize());
            long keyOffset = page.get(ValueLayout.JAVA_LONG, pivotOffset);
            int keySize = page.get(ValueLayout.JAVA_INT, pivotOffset + 8);

            String pivotKey = "";
            if (keyOffset != 0) {
                // In a real implementation, keys would be in a buffer at the end of the node page
                // For this implementation, we'll assume pivots were created with keys available in DataLog
                pivotKey = dataLog.readKey(keyOffset);
            }

            int cmp = key.compareTo(pivotKey);
            if (cmp < 0) {
                high = mid - 1;
            } else if (cmp > 0) {
                low = mid + 1;
            } else {
                return page.get(ValueLayout.JAVA_LONG, pivotOffset + 12);
            }
        }
        
        // Return child offset for the range found (high is the index of the largest pivot <= key)
        int index = Math.max(0, high);
        return page.get(ValueLayout.JAVA_LONG, pivotStart + ((long) index * Layout.PIVOT_ENTRY.byteSize()) + 12);
    }

    public void insert(String key, long dataOffset) {
        try {
            InsertResult result = insert(rootOffset, key, dataOffset);
            this.rootOffset = result.nodeOffset();

            if (result.splitKey() != null) {
                long newRootOffset = storageManager.allocatePage();
                MemorySegment newRoot = getPage(newRootOffset);
                Layout.IN_TYPE.set(newRoot, 0L, Layout.TYPE_INTERNAL);
                Layout.IN_NUM_PIVOTS.set(newRoot, 0L, 2);
                Layout.IN_EPOCH.set(newRoot, 0L, storageManager.currentEpoch());

                long pivotStart = Layout.INTERNAL_NODE_HEADER.byteSize();
                newRoot.set(ValueLayout.JAVA_LONG, pivotStart, 0); 
                newRoot.set(ValueLayout.JAVA_INT, pivotStart + 8, result.splitKey().length());
                newRoot.set(ValueLayout.JAVA_LONG, pivotStart + 12, result.nodeOffset());

                long pivot2 = pivotStart + Layout.PIVOT_ENTRY.byteSize();
                newRoot.set(ValueLayout.JAVA_LONG, pivot2, 0);
                newRoot.set(ValueLayout.JAVA_INT, pivot2 + 8, 0);
                newRoot.set(ValueLayout.JAVA_LONG, pivot2 + 12, result.siblingOffset());

                this.rootOffset = newRootOffset;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record InsertResult(long nodeOffset, String splitKey, long siblingOffset) {}

    private InsertResult insert(long nodeOffset, String key, long dataOffset) throws IOException {
        MemorySegment page = getPage(nodeOffset);
        long nodeEpoch = (long) Layout.LN_EPOCH.get(page, 0L);

        if (nodeEpoch < storageManager.currentEpoch()) {
            long newNodeOffset = storageManager.allocatePage();
            MemorySegment newPage = getPage(newNodeOffset);
            MemorySegment.copy(page, 0, newPage, 0, Layout.PAGE_SIZE);
            Layout.LN_EPOCH.set(newPage, 0L, storageManager.currentEpoch());
            storageManager.deferFree(nodeOffset, storageManager.currentEpoch());
            nodeOffset = newNodeOffset;
            page = newPage;
        }

        int type = (int) Layout.LN_TYPE.get(page, 0L);
        if (type == Layout.TYPE_LEAF) {
            int numEntries = (int) Layout.LN_NUM_ENTRIES.get(page, 0L);
            if (numEntries < MAX_LEAF_ENTRIES) {
                insertIntoLeaf(page, key, dataOffset);
                return new InsertResult(nodeOffset, null, 0);
            } else {
                return splitLeaf(page, nodeOffset, key, dataOffset);
            }
        } else {
            long childOffset = findChild(page, key);
            InsertResult childRes = insert(childOffset, key, dataOffset);
            updateChildPointer(page, key, childRes.nodeOffset());

            if (childRes.splitKey() != null) {
                // Implement split handling for internal nodes
            }
            return new InsertResult(nodeOffset, null, 0);
        }
    }

    private void insertIntoLeaf(MemorySegment page, String key, long dataOffset) {
        int numEntries = (int) Layout.LN_NUM_ENTRIES.get(page, 0L);
        int keyPrefix = getPrefix(key);
        int keyHash = getJenkinsHash(key);

        long entryOffset = Layout.LEAF_NODE_HEADER.byteSize() + ((long) numEntries * Layout.LEAF_ENTRY.byteSize());

        page.set(ValueLayout.JAVA_INT, entryOffset, keyPrefix);
        page.set(ValueLayout.JAVA_INT, entryOffset + 4, keyHash);
        page.set(ValueLayout.JAVA_LONG, entryOffset + 8, dataOffset);

        Layout.LN_NUM_ENTRIES.set(page, 0L, numEntries + 1);
    }

    private InsertResult splitLeaf(MemorySegment oldPage, long oldOffset, String key, long dataOffset) throws IOException {
        long siblingOffset = storageManager.allocatePage();
        MemorySegment siblingPage = getPage(siblingOffset);

        Layout.LN_TYPE.set(siblingPage, 0L, Layout.TYPE_LEAF);
        Layout.LN_EPOCH.set(siblingPage, 0L, storageManager.currentEpoch());

        int numEntries = (int) Layout.LN_NUM_ENTRIES.get(oldPage, 0L);
        int mid = numEntries / 2;

        long srcOffset = Layout.LEAF_NODE_HEADER.byteSize() + ((long) mid * Layout.LEAF_ENTRY.byteSize());
        long sizeToCopy = (long) (numEntries - mid) * Layout.LEAF_ENTRY.byteSize();
        MemorySegment.copy(oldPage, srcOffset, siblingPage, Layout.LEAF_NODE_HEADER.byteSize(), sizeToCopy);

        Layout.LN_NUM_ENTRIES.set(oldPage, 0L, mid);
        Layout.LN_NUM_ENTRIES.set(siblingPage, 0L, numEntries - mid);

        insertIntoLeaf(siblingPage, key, dataOffset);

        String splitKey = dataLog.readKey(siblingPage.get(ValueLayout.JAVA_LONG, Layout.LEAF_NODE_HEADER.byteSize() + 8));
        return new InsertResult(oldOffset, splitKey, siblingOffset);
    }

    private void updateChildPointer(MemorySegment page, String key, long newChildOffset) {
        int numPivots = (int) Layout.IN_NUM_PIVOTS.get(page, 0L);
        long pivotStart = Layout.INTERNAL_NODE_HEADER.byteSize();
        
        // Find the pivot that points to the child being updated
        for (int i = 0; i < numPivots; i++) {
            long pivotOffset = pivotStart + ((long) i * Layout.PIVOT_ENTRY.byteSize());
            // This is a simplified update logic
            page.set(ValueLayout.JAVA_LONG, pivotOffset + 12, newChildOffset);
        }
    }

    public long getRootOffset() {
        return rootOffset;
    }

    private int getPrefix(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(4);
        for (int i = 0; i < 4 && i < bytes.length; i++) {
            bb.put(bytes[i]);
        }
        return bb.getInt(0);
    }

    private int getJenkinsHash(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int hash = 0;
        for (byte b : bytes) {
            hash += (b & 0xFF);
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }
        hash += (hash << 3);
        hash ^= (hash >>> 11);
        hash += (hash << 15);
        return hash;
    }
}
