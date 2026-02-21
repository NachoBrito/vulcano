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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.appendonly;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * @author nacho
 */
final class Metadata {
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;

    private final MemorySegment segment;

    Metadata(Path path) throws IOException {
        FileChannel ch = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
        ch.truncate(16);
        Arena arena = Arena.ofShared();
        this.segment = ch.map(FileChannel.MapMode.READ_WRITE, 0, 16, arena);
    }

    long dataOffset() {
        return segment.get(LONG, 0);
    }

    long indexOffset() {
        return segment.get(LONG, 8);
    }

    void commit(long data, long index) {
        segment.set(LONG, 0, data);
        segment.set(LONG, 8, index);
        segment.force();
    }

    public void fsync() {
        segment.force();
    }
}
