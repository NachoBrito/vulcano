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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * A thread-safe, paged array of primitive longs that avoids boxing and provides lock-free reads.
 * It uses VarHandles to ensure memory visibility and atomic operations on the underlying primitive arrays.
 *
 * @author nacho
 */
public final class PagedLongArray {
    private static final int DEFAULT_PAGE_SIZE = 4096;
    private static final VarHandle LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);

    private final int pageSize;
    private final int pageShift;
    private final int pageMask;

    private volatile long[][] pages;
    private final Object expansionLock = new Object();

    public PagedLongArray() {
        this(DEFAULT_PAGE_SIZE);
    }

    public PagedLongArray(int pageSize) {
        if (Integer.bitCount(pageSize) != 1) {
            throw new IllegalArgumentException("pageSize must be a power of 2");
        }
        this.pageSize = pageSize;
        this.pageShift = Integer.numberOfTrailingZeros(pageSize);
        this.pageMask = pageSize - 1;
        this.pages = new long[0][];
    }

    /**
     * Sets the value at the specified index. If the index exceeds current capacity,
     * the array is expanded by adding new pages.
     */
    public void set(long index, long value) {
        int pageIndex = (int) (index >> pageShift);
        int elementIndex = (int) (index & pageMask);

        ensureCapacity(pageIndex);
        long[] page = pages[pageIndex];
        LONG_ARRAY_HANDLE.setRelease(page, elementIndex, value);
    }

    /**
     * Retrieves the value at the specified index. Returns 0 if the index has not been set.
     */
    public long get(long index) {
        int pageIndex = (int) (index >> pageShift);
        int elementIndex = (int) (index & pageMask);

        long[][] currentPages = this.pages; // Read volatile once
        if (pageIndex >= currentPages.length) {
            return 0;
        }

        long[] page = currentPages[pageIndex];
        if (page == null) {
            return 0;
        }

        return (long) LONG_ARRAY_HANDLE.getAcquire(page, elementIndex);
    }

    private void ensureCapacity(int pageIndex) {
        if (pageIndex >= pages.length) {
            synchronized (expansionLock) {
                if (pageIndex >= pages.length) {
                    int newLength = Math.max(pageIndex + 1, pages.length * 2);
                    long[][] newPages = Arrays.copyOf(pages, newLength);
                    for (int i = pages.length; i < newLength; i++) {
                        newPages[i] = new long[pageSize];
                    }
                    this.pages = newPages; // Volatile write
                }
            }
        }
    }

    /**
     * Returns the total capacity of the array (number of elements that can be stored in current pages).
     */
    public long capacity() {
        return (long) pages.length * pageSize;
    }
}
