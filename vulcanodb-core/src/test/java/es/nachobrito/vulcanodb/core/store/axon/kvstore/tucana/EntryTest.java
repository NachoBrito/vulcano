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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author nacho
 */
class EntryTest {

    @Test
    void expectIntegerEntriesCreatedProperly() {
        var key = "theKey";
        var value = 5;
        var entry = Entry.of(key, value);
        assertEquals(key, Entry.readKey(entry));
        assertEquals(value, Entry.readIntValue(entry));
    }

    @Test
    void expectStringEntriesCreatedProperly() {
        var key = "theKey";
        var value = "theValue";
        var entry = Entry.of(key, value);
        assertEquals(key, Entry.readKey(entry));
        assertEquals(value, Entry.readStringValue(entry));
    }

    @Test
    void expectFloatEntriesCreatedProperly() {
        var key = "theKey";
        var value = .5f;
        var entry = Entry.of(key, value);
        assertEquals(key, Entry.readKey(entry));
        assertEquals(value, Entry.readFloatValue(entry));
    }

    @Test
    void expectFloatArrayEntriesCreatedProperly() {
        var key = "theKey";
        var value = new float[]{1.0f, 2.0f, 3.0f};
        var entry = Entry.of(key, value);
        assertEquals(key, Entry.readKey(entry));
        assertArrayEquals(value, Entry.readFloatArrayValue(entry));
    }

    @Test
    void expectFloatMatrixEntriesCreatedProperly() {
        var key = "theKey";
        var value = new float[][]{{1.0f, 2.0f, 3.0f}, {2.0f, 4.0f, 6.0f}};
        var entry = Entry.of(key, value);
        assertEquals(key, Entry.readKey(entry));
        assertArrayEquals(value, Entry.readFloatMatrixValue(entry));
    }
}