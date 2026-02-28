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

/**
 * Implementation of {@link TucanaBuffer} that manages multiple memory pages via a {@link PageManager}.
 */
public class PagedTucanaBuffer implements TucanaBuffer {

    private final PageManager pageManager;

    public PagedTucanaBuffer(PageManager pageManager) {
        this.pageManager = pageManager;
    }

    private MemorySegment getPage(long offset) {
        int pageSize = pageManager.pageSize();
        int pageIndex = (int) (offset / pageSize);
        return pageManager.getPage(pageIndex);
    }

    private long getPageOffset(long offset) {
        return offset % pageManager.pageSize();
    }

    @Override
    public byte getByte(long offset) {
        return getPage(offset).get(ValueLayout.JAVA_BYTE, getPageOffset(offset));
    }

    @Override
    public void putByte(long offset, byte value) {
        getPage(offset).set(ValueLayout.JAVA_BYTE, getPageOffset(offset), value);
    }

    @Override
    public int getInt(long offset) {
        return getPage(offset).get(ValueLayout.JAVA_INT_UNALIGNED, getPageOffset(offset));
    }

    @Override
    public void putInt(long offset, int value) {
        getPage(offset).set(ValueLayout.JAVA_INT_UNALIGNED, getPageOffset(offset), value);
    }

    @Override
    public long getLong(long offset) {
        return getPage(offset).get(ValueLayout.JAVA_LONG_UNALIGNED, getPageOffset(offset));
    }

    @Override
    public void putLong(long offset, long value) {
        getPage(offset).set(ValueLayout.JAVA_LONG_UNALIGNED, getPageOffset(offset), value);
    }

    @Override
    public void force() {
        pageManager.flush();
    }
}
