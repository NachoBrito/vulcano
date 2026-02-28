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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PagedTucanaBuffer}.
 */
class PagedTucanaBufferTest {

    private static final int PAGE_SIZE = 1024;
    private PageManager pageManager;
    private PagedTucanaBuffer buffer;

    @BeforeEach
    void setUp() {
        pageManager = mock(PageManager.class);
        when(pageManager.pageSize()).thenReturn(PAGE_SIZE);
        buffer = new PagedTucanaBuffer(pageManager);
    }

    @Test
    void testReadWriteAcrossPages() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment page0 = arena.allocate(PAGE_SIZE);
            MemorySegment page1 = arena.allocate(PAGE_SIZE);

            when(pageManager.getPage(0)).thenReturn(page0);
            when(pageManager.getPage(1)).thenReturn(page1);

            // Write in first page
            buffer.putInt(0, 123);
            assertEquals(123, buffer.getInt(0));
            // One call for put, one for get
            verify(pageManager, times(2)).getPage(0);

            // Write in second page
            buffer.putInt(PAGE_SIZE, 456);
            assertEquals(456, buffer.getInt(PAGE_SIZE));
            verify(pageManager, times(2)).getPage(1);

            buffer.putLong(PAGE_SIZE + 10, 789L);
            assertEquals(789L, buffer.getLong(PAGE_SIZE + 10));
        }
    }

    @Test
    void testFlushDelegation() {
        buffer.force();
        verify(pageManager).flush();
    }

}
