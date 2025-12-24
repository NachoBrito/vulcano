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

package es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.store.kvstore;

import es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon.kvstore.KeyValueStore;
import es.nachobrito.vulcanodb.core.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class KeyValueStoreTest {

    private Path path;
    private KeyValueStore kvstore;

    @BeforeEach
    void setup() throws IOException {
        path = Files.createTempDirectory("vulcanodb-test");
        kvstore = new KeyValueStore(path);
    }

    @AfterEach
    void tearDown() throws Exception {
        kvstore.close();
        FileUtils.deleteRecursively(path.toFile());
    }

    @Test
    void expectReadsAndWritesInts() {
        kvstore.putInt("int1", 1);
        kvstore.putInt("int2", 2);
        kvstore.putInt("int3", 3);

        assertEquals(2, kvstore.getInt("int2").get());
        assertEquals(3, kvstore.getInt("int3").get());
        assertEquals(1, kvstore.getInt("int1").get());
    }

    @Test
    void expectReadsAndWritesStrings() {
        kvstore.putString("string1", "string1 value!");
        kvstore.putString("string2", "string2 value is larger!");
        kvstore.putString("string3", "string3 value is larger still!");

        assertEquals("string2 value is larger!", kvstore.getString("string2").get());
        assertEquals("string1 value!", kvstore.getString("string1").get());
        assertEquals("string3 value is larger still!", kvstore.getString("string3").get());
    }


    @Test
    void expectReadsAndWritesVectors() {
        kvstore.putFloatArray("vector", new float[]{1, 2, 3});
        kvstore.putFloatArray("vector1", new float[]{1});
        kvstore.putFloatArray("vector 2", new float[]{1, 2, 3, 4, 5});

        assertArrayEquals(new float[]{1}, kvstore.getFloatArray("vector1").get());
        assertArrayEquals(new float[]{1, 2, 3, 4, 5}, kvstore.getFloatArray("vector 2").get());
        assertArrayEquals(new float[]{1, 2, 3}, kvstore.getFloatArray("vector").get());
    }

    @Test
    void expectReadsAndWritesMatrices() {
        kvstore.putFloatMatrix("matrix1", new float[][]{{1, 2, 3}, {4, 5, 6}});
        kvstore.putFloatMatrix("m2", new float[][]{{1}, {2}, {3}, {4}});
        kvstore.putFloatMatrix("mat3", new float[][]{{0, 0}, {1, 1}});

        assertArrayEquals(new float[][]{{0, 0}, {1, 1}}, kvstore.getFloatMatrix("mat3").get());
        assertArrayEquals(new float[][]{{1, 2, 3}, {4, 5, 6}}, kvstore.getFloatMatrix("matrix1").get());
        assertArrayEquals(new float[][]{{1}, {2}, {3}, {4}}, kvstore.getFloatMatrix("m2").get());
    }

    @Test
    void expectReadsAndWritesMixed() {
        kvstore.putFloatArray("vector", new float[]{1, 2, 3});
        kvstore.putString("string1", "string1 value!");
        kvstore.putInt("int1", 1);
        kvstore.putFloatMatrix("mtx", new float[][]{{1, 2}, {3, 4}});

        assertEquals("string1 value!", kvstore.getString("string1").get());
        assertEquals(1, kvstore.getInt("int1").get());
        assertArrayEquals(new float[][]{{1, 2}, {3, 4}}, kvstore.getFloatMatrix("mtx").get());
        assertArrayEquals(new float[]{1, 2, 3}, kvstore.getFloatArray("vector").get());
    }

    @Test
    void expectRemoves() {
        kvstore.putFloatArray("vector", new float[]{1, 2, 3});
        assertArrayEquals(new float[]{1, 2, 3}, kvstore.getFloatArray("vector").get());

        kvstore.remove("vector");
        assertTrue(kvstore.getFloatArray("vector").isEmpty());
    }

    @Test
    void expectUpdates() {
        kvstore.putFloatArray("vector", new float[]{1, 2, 3});
        assertArrayEquals(new float[]{1, 2, 3}, kvstore.getFloatArray("vector").get());

        kvstore.putFloatArray("vector", new float[]{9, 9, 9});
        assertArrayEquals(new float[]{9, 9, 9}, kvstore.getFloatArray("vector").get());
    }


    @Test
    void expectAcceptsEmptyValues() {
        kvstore.putFloatArray("vector", new float[]{});
        kvstore.putString("string1", "");
        kvstore.putInt("int1", 0);
        kvstore.putFloatMatrix("mtx1", new float[][]{});
        kvstore.putFloatMatrix("mtx2", new float[][]{{}, {}});

        assertEquals("", kvstore.getString("string1").get());
        assertEquals(0, kvstore.getInt("int1").get());
        assertArrayEquals(new float[][]{{}, {}}, kvstore.getFloatMatrix("mtx2").get());
        assertArrayEquals(new float[][]{}, kvstore.getFloatMatrix("mtx1").get());
        assertArrayEquals(new float[]{}, kvstore.getFloatArray("vector").get());
    }
}