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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SegmentManagerTest {

    @TempDir
    Path tempDir;

    private SegmentManager segmentManager;
    private final long initialSize = 1024 * 1024; // 1 MB

    @BeforeEach
    void setUp() throws IOException {
        segmentManager = new SegmentManager(tempDir.resolve("test.seg"), initialSize);
    }

    @AfterEach
    void tearDown() {
        segmentManager.close();
    }

    @Test
    void testAllocatePage() {
        long offset1 = segmentManager.allocatePage();
        long offset2 = segmentManager.allocatePage();

        assertNotEquals(offset1, offset2);
        assertTrue(offset1 >= Layout.PAGE_SIZE); // Page 0 is reserved
        assertTrue(offset2 >= Layout.PAGE_SIZE);
    }

    @Test
    void testGetPage() {
        long offset = segmentManager.allocatePage();
        MemorySegment page = segmentManager.getPage(offset);

        assertNotNull(page);
        assertEquals(Layout.PAGE_SIZE, page.byteSize());
    }

    @Test
    void testSuperblock() {
        MemorySegment superblock = segmentManager.getSuperblock();
        assertNotNull(superblock);
        assertEquals(Layout.PAGE_SIZE, superblock.byteSize());
    }

    @Test
    void testDeferredFree() {
        long offset = segmentManager.allocatePage();
        segmentManager.deferFree(offset, 1);
        
        // Should not be reusable yet
        long nextOffset = segmentManager.allocatePage();
        assertNotEquals(offset, nextOffset);

        segmentManager.processFreeLog(1);
        
        // Now it should be free
        long reusableOffset = segmentManager.allocatePage();
        assertEquals(offset, reusableOffset);
    }

    @Test
    void testEpochIncrement() {
        long epoch = segmentManager.currentEpoch();
        segmentManager.incrementEpoch();
        assertEquals(epoch + 1, segmentManager.currentEpoch());
    }
}
