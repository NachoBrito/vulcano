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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.storage.paging;

import java.lang.foreign.MemorySegment;

/**
 * Manages the lifecycle and retrieval of memory segments (pages).
 */
public interface PageManager {
    /**
     * Returns the memory segment for the given page index.
     * Implementations may create the page on-demand if it doesn't exist.
     */
    MemorySegment getPage(int pageIndex);

    /**
     * Returns the fixed size of each page.
     */
    int pageSize();

    /**
     * Persists all dirty pages to the underlying storage.
     */
    void flush();
}
