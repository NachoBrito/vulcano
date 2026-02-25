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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataLogTest {

    @TempDir
    Path tempDir;

    private SegmentManager segmentManager;
    private DataLog dataLog;

    @BeforeEach
    void setUp() throws IOException {
        segmentManager = new SegmentManager(tempDir.resolve("test.seg"), 1024 * 1024);
        dataLog = new DataLog(segmentManager, 0);
    }

    @AfterEach
    void tearDown() {
        segmentManager.close();
    }

    @Test
    void testWriteAndReadString() {
        String key = "testKey";
        String value = "testValue";
        byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        long offset = dataLog.write(key, valueBytes, (byte) 1);

        assertEquals(key, dataLog.readKey(offset));
        assertArrayEquals(valueBytes, dataLog.readValue(offset));
        assertEquals(value, new String(dataLog.readValue(offset), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testWriteAndReadBytes() {
        String key = "bytesKey";
        byte[] value = new byte[]{1, 2, 3, 4, 5};

        long offset = dataLog.write(key, value, (byte) 0);

        assertEquals(key, dataLog.readKey(offset));
        assertArrayEquals(value, dataLog.readValue(offset));
    }

    @Test
    void testMultipleWrites() {
        long offset1 = dataLog.write("key1", "val1".getBytes(), (byte) 1);
        long offset2 = dataLog.write("key2", "val2".getBytes(), (byte) 1);

        assertTrue(offset2 > offset1);
        assertEquals("key1", dataLog.readKey(offset1));
        assertEquals("key2", dataLog.readKey(offset2));
        assertEquals("val1", new String(dataLog.readValue(offset1)));
        assertEquals("val2", new String(dataLog.readValue(offset2)));
    }
}
