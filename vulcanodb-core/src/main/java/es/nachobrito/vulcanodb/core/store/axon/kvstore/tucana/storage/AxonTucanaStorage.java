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

import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer.BoundedTucanaBuffer;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer.PagedTucanaBuffer;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer.TucanaBuffer;
import es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.paging.FilePageManager;
import java.lang.foreign.MemorySegment;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concrete implementation of {@link TucanaStorage} for Axon.
 * <p>
 * This class implements a persistent, write-optimized storage layer based on the Tucana
 * architecture. It uses a dual-superblock mechanism for atomic commits and a paged
 * memory-mapped file for efficient data access.
 * <p>
 * The storage is divided into a header region (containing two superblocks) and a data region.
 * All writes are Copy-on-Write (CoW), and changes are only made visible when a commit
 * operation successfully swaps the active superblock.
 */
public class AxonTucanaStorage implements TucanaStorage {

    /** The size of each memory page (1MB). */
    private static final int PAGE_SIZE = 1024 * 1024;
    /** The offset where the data region starts, leaving 1MB for superblocks and headers. */
    private static final long DATA_REGION_OFFSET = 1024 * 1024;

    /** The page manager that handles memory mapping of the physical file. */
    private final FilePageManager pageManager;
    /** A paged buffer view over the entire database file. */
    private final PagedTucanaBuffer buffer;

    /** The two superblocks used for the atomic commit protocol. */
    private final Superblock[] superblocks = new Superblock[2];
    /** The index (0 or 1) of the currently active and consistent superblock. */
    private final AtomicInteger activeSuperblockIndex = new AtomicInteger(-1);

    /** The current write offset within the data region for the current epoch. */
    private final AtomicLong allocatorOffset = new AtomicLong(DATA_REGION_OFFSET);

    /**
     * Initializes the storage using the specified file path.
     * <p>
     * It maps the header page and identifies the latest valid superblock to restore
     * the database state.
     * @param path the path to the database file.
     */
    public AxonTucanaStorage(Path path) {
        this.pageManager = new FilePageManager(path, PAGE_SIZE);
        this.buffer = new PagedTucanaBuffer(pageManager);

        // Initialize superblocks from the first page
        MemorySegment headerPage = pageManager.getPage(0);
        superblocks[0] = new Superblock(headerPage.asSlice(0, Superblock.SIZE));
        superblocks[1] = new Superblock(headerPage.asSlice(Superblock.SIZE, Superblock.SIZE));

        initialize();
    }

    /**
     * Locates the active superblock by comparing epochs and verifying checksums.
     * If no valid superblock is found, it initializes a new database state.
     */
    private void initialize() {
        boolean sb0Valid = superblocks[0].isValid();
        boolean sb1Valid = superblocks[1].isValid();

        if (sb0Valid && sb1Valid) {
            // Both valid: choose the one with the higher epoch
            activeSuperblockIndex.set(superblocks[0].epoch() >= superblocks[1].epoch() ? 0 : 1);
        } else if (sb0Valid) {
            activeSuperblockIndex.set(0);
        } else if (sb1Valid) {
            activeSuperblockIndex.set(1);
        } else {
            // First time initialization: setup SB0
            activeSuperblockIndex.set(0);
            superblocks[0].setMagic();
            superblocks[0].setEpoch(0);
            superblocks[0].setRootOffset(-1);
            superblocks[0].setAllocatorOffset(DATA_REGION_OFFSET);
            superblocks[0].updateChecksum();
        }
        // Restore allocator position from the active state
        allocatorOffset.set(superblocks[activeSuperblockIndex.get()].allocatorOffset());
    }

    /** @return the currently active superblock. */
    private Superblock activeSB() {
        return superblocks[activeSuperblockIndex.get()];
    }

    /** @return the inactive superblock used for the next commit. */
    private Superblock inactiveSB() {
        return superblocks[1 - activeSuperblockIndex.get()];
    }

    /**
     * {@inheritDoc}
     * <p>
     * Allocates space from the data region and returns a bounded buffer.
     */
    @Override
    public TucanaBuffer allocate(long size) {
        long offset = allocatorOffset.getAndAdd(size);
        return new BoundedTucanaBuffer(buffer, offset);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a bounded buffer for the specified offset.
     */
    @Override
    public TucanaBuffer getBuffer(long offset, long size) {
        return new BoundedTucanaBuffer(buffer, offset);
    }

    /**
     * Commits all pending changes atomically.
     * <p>
     * The process follows these steps:
     * 1. Prepare the inactive superblock with the incremented epoch and new offsets.
     * 2. Flush all dirty memory pages to disk (including new data and index nodes).
     * 3. Swap the active superblock index.
     */
    @Override
    public void commit() {
        long nextEpoch = activeSB().epoch() + 1;
        Superblock nextSB = inactiveSB();

        nextSB.setMagic();
        nextSB.setEpoch(nextEpoch);
        nextSB.setRootOffset(activeSB().rootOffset());
        nextSB.setAllocatorOffset(allocatorOffset.get());
        nextSB.updateChecksum();

        pageManager.flush(); // Sync all data pages before swapping the superblock
        activeSuperblockIndex.updateAndGet(index -> 1 - index);
    }

    /**
     * Rolls back any uncommitted changes by restoring the allocator offset
     * to the position recorded in the active superblock.
     */
    @Override
    public void rollback() {
        allocatorOffset.set(activeSB().allocatorOffset());
    }

    @Override
    public long currentEpoch() {
        return activeSB().epoch();
    }

    @Override
    public Optional<Long> getRootOffset(long epoch) {
        if (epoch == activeSB().epoch()) {
            long root = activeSB().rootOffset();
            return root == -1 ? Optional.empty() : Optional.of(root);
        }
        return Optional.empty();
    }

    @Override
    public void setRootOffset(long offset) {
        activeSB().setRootOffset(offset);
        inactiveSB().setRootOffset(offset);
        pageManager.flush(); // Ensure root offset update is visible on disk
    }

    /**
     * Reads a data block from the storage.
     * <p>
     * The block is expected to have a 4-byte integer header indicating its size.
     * @param offset the offset of the block relative to the start of the data region.
     * @return a ByteBuffer containing the block data.
     */
    @Override
    public ByteBuffer read(long offset) {
        // Read size prefix from the data region
        int size = buffer.getInt(offset);
        long dataOffset = offset + 4;

        ByteBuffer result = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            result.put(buffer.getByte(dataOffset + i));
        }
        return result.flip();
    }

    /**
     * Writes a data block to the storage.
     * <p>
     * All writes are appended to the end of the data region. The block is prefixed
     * with its 4-byte size.
     * @param data the data to write.
     * @return the offset where the block was written.
     */
    @Override
    public long write(ByteBuffer data) {
        long size = data.remaining();
        long totalSize = 4 + size; // 4 bytes for length prefix
        long offset = allocatorOffset.getAndAdd(totalSize);

        buffer.putInt(offset, (int) size);
        buffer.putBuffer(offset + 4, data);

        return offset;
    }

    /**
     * Closes the storage and its underlying page manager.
     */
    @Override
    public void close() throws Exception {
        pageManager.close();
    }
}
