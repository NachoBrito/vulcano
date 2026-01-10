/*
 *    Copyright 2025 Nacho Brito
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

package es.nachobrito.vulcanodb.core.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A thread-safe, paged array of primitive longs that avoids boxing and provides lock-free reads.
 * It uses memory-mapped files for persistence and VarHandles for memory visibility.
 *
 * @author nacho
 */
public final class PagedLongArray implements AutoCloseable {
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private final int pageSize;
    private final int pageShift;
    private final int pageMask;

    private volatile MemorySegment[] pages;
    private final List<Arena> arenas = new ArrayList<>();
    private final Object expansionLock = new Object();

    private final Path basePath;

    public PagedLongArray(Path basePath) {
        this(DEFAULT_PAGE_SIZE, basePath);
    }

    public PagedLongArray(int pageSize, Path basePath) {
        if (Integer.bitCount(pageSize) != 1) {
            throw new IllegalArgumentException("pageSize must be a power of 2");
        }
        if (basePath == null) {
            throw new IllegalArgumentException("basePath cannot be null");
        }
        this.pageSize = pageSize;
        this.pageShift = Integer.numberOfTrailingZeros(pageSize);
        this.pageMask = pageSize - 1;
        this.basePath = basePath;
        this.pages = new MemorySegment[0];

        try {
            Files.createDirectories(basePath);
            loadExistingPages();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadExistingPages() throws IOException {
        int pageIdx = 0;
        while (true) {
            Path pagePath = basePath.resolve("long-page-" + pageIdx + ".dat");
            if (!Files.exists(pagePath)) {
                break;
            }
            openPage(pageIdx, pagePath);
            pageIdx++;
        }
    }

    /**
     * Sets the value at the specified index. If the index exceeds current capacity,
     * the array is expanded by adding new pages.
     */
    public void set(long index, long value) {
        int pageIndex = (int) (index >> pageShift);
        int elementIndex = (int) (index & pageMask);

        ensureCapacity(pageIndex);
        MemorySegment page = pages[pageIndex];
        page.set(ValueLayout.JAVA_LONG_UNALIGNED, (long) elementIndex * Long.BYTES, value);
    }

    /**
     * Retrieves the value at the specified index. Returns 0 if the index has not been set.
     */
    public long get(long index) {
        int pageIndex = (int) (index >> pageShift);
        int elementIndex = (int) (index & pageMask);

        MemorySegment[] currentPages = this.pages; // Read volatile once
        if (pageIndex >= currentPages.length) {
            return 0;
        }

        MemorySegment page = currentPages[pageIndex];
        if (page == null) {
            return 0;
        }

        return page.get(ValueLayout.JAVA_LONG_UNALIGNED, (long) elementIndex * Long.BYTES);
    }

    private void ensureCapacity(int pageIndex) {
        if (pageIndex >= pages.length) {
            synchronized (expansionLock) {
                while (pageIndex >= pages.length) {
                    int newLength = Math.max(pageIndex + 1, pages.length * 2);
                    if (newLength == 0) newLength = 1;
                    MemorySegment[] newPages = Arrays.copyOf(pages, newLength);
                    for (int i = pages.length; i < newLength; i++) {
                        Path pagePath = basePath.resolve("long-page-" + i + ".dat");
                        try {
                            newPages[i] = createMappedPage(pagePath);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    this.pages = newPages; // Volatile write
                }
            }
        }
    }

    private void openPage(int pageIdx, Path path) throws IOException {
        synchronized (expansionLock) {
            if (pageIdx >= pages.length) {
                pages = Arrays.copyOf(pages, pageIdx + 1);
            }
            pages[pageIdx] = createMappedPage(path);
        }
    }

    private MemorySegment createMappedPage(Path path) throws IOException {
        long byteSize = (long) pageSize * Long.BYTES;
        FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (ch.size() < byteSize) {
            ch.truncate(byteSize);
        }
        Arena arena = Arena.ofShared();
        MemorySegment page = ch.map(FileChannel.MapMode.READ_WRITE, 0, byteSize, arena);
        arenas.add(arena);
        return page;
    }

    /**
     * Returns the total capacity of the array (number of elements that can be stored in current pages).
     */
    public long capacity() {
        return (long) pages.length * pageSize;
    }

    @Override
    public void close() {
        synchronized (expansionLock) {
            for (Arena arena : arenas) {
                if (arena.scope().isAlive()) {
                    arena.close();
                }
            }
            arenas.clear();
        }
    }
}
