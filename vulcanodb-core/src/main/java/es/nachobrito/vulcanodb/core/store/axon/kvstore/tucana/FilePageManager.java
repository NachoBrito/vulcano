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
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PageManager} that uses memory-mapped files.
 * <p>
 * This class manages a single physical file and maps logical sections (pages) of it into
 * the process's address space as needed. It leverages Java's Foreign Function & Memory (FFM) API
 * for high-performance, disk-backed storage.
 * <p>
 * Pages are mapped lazily upon the first request to {@link #getPage(int)} and are cached for
 * subsequent access.
 */
public class FilePageManager implements PageManager, AutoCloseable {

    /** The fixed size of each page in bytes. */
    private final int pageSize;
    /** The file channel used to perform mapping operations. */
    private final FileChannel channel;
    /** The arena that manages the lifecycle of all mapped memory segments. */
    private final Arena arena;
    /** A cache of currently mapped pages, indexed by their page index. */
    private final Map<Integer, MemorySegment> pages = new ConcurrentHashMap<>();

    /**
     * Creates a new FilePageManager for the specified file.
     * @param path the path to the database file.
     * @param pageSize the size of each page in bytes.
     * @throws UncheckedIOException if the file cannot be opened.
     */
    public FilePageManager(Path path, int pageSize) {
        this.pageSize = pageSize;
        try {
            this.channel = FileChannel.open(path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE);
            this.arena = Arena.ofShared();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the page is not already in memory, it will be mapped using {@link FileChannel#map(FileChannel.MapMode, long, long, Arena)}.
     */
    @Override
    public MemorySegment getPage(int pageIndex) {
        return pages.computeIfAbsent(pageIndex, idx -> {
            try {
                long offset = (long) idx * pageSize;
                return channel.map(FileChannel.MapMode.READ_WRITE, offset, pageSize, arena);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Instructs the operating system to synchronize all dirty pages back to the physical disk.
     */
    @Override
    public void flush() {
        pages.values().forEach(MemorySegment::force);
    }

    /**
     * Closes the manager, flushes all pages to disk, and releases all memory mappings.
     * @throws Exception if an error occurs during closing.
     */
    @Override
    public void close() throws Exception {
        flush();
        arena.close();
        channel.close();
    }
}
