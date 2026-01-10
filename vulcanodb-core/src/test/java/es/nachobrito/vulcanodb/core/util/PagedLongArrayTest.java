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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PagedLongArrayTest {

    @Test
    void testBasicSetAndGet() {
        PagedLongArray array = new PagedLongArray(4);
        array.set(0, 100);
        array.set(1, 200);
        array.set(2, 300);

        assertEquals(100, array.get(0));
        assertEquals(200, array.get(1));
        assertEquals(300, array.get(2));
        assertEquals(0, array.get(3));
    }

    @Test
    void testExpansion() {
        PagedLongArray array = new PagedLongArray(4);
        array.set(0, 1);
        array.set(4, 2); // Should trigger expansion to second page
        array.set(8, 3); // Should trigger expansion to third page

        assertEquals(1, array.get(0));
        assertEquals(0, array.get(1));
        assertEquals(2, array.get(4));
        assertEquals(3, array.get(8));
        assertTrue(array.capacity() >= 12);
    }

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PagedLongArray(3));
        assertThrows(IllegalArgumentException.class, () -> new PagedLongArray(100));
        assertDoesNotThrow(() -> new PagedLongArray(1024));
    }

    @Test
    void testGetUnsetIndex() {
        PagedLongArray array = new PagedLongArray(4);
        assertEquals(0, array.get(0));
        assertEquals(0, array.get(100));
    }

    @Test
    void testConcurrentWrites() throws InterruptedException, ExecutionException {
        int threadCount = 8;
        int elementsPerThread = 10000;
        PagedLongArray array = new PagedLongArray(1024);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            tasks.add(() -> {
                for (int i = 0; i < elementsPerThread; i++) {
                    long index = (long) threadId * elementsPerThread + i;
                    array.set(index, index * 2);
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < elementsPerThread; i++) {
                long index = (long) t * elementsPerThread + i;
                assertEquals(index * 2, array.get(index));
            }
        }
    }

    @Test
    void testConcurrentExpansion() throws InterruptedException {
        int threadCount = 16;
        PagedLongArray array = new PagedLongArray(2); // Small page size to force frequent expansion
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            final int index = i * 100;
            executor.submit(() -> {
                try {
                    latch.await();
                    array.set(index, index);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (int i = 0; i < threadCount; i++) {
            assertEquals(i * 100, array.get(i * 100));
        }
    }
}
