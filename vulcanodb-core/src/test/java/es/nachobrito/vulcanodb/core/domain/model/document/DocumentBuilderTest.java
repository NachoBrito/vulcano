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

package es.nachobrito.vulcanodb.core.domain.model.document;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class DocumentBuilderTest {

    @Test
    void expectWithMapCreatesDocument() {
        var now = ZonedDateTime.now();
        Map<String, Object> fields = Map.of(
                "integer", 1,
                "string", "a string",
                "vector1", new float[]{1.0f, 2.0f},
                "vector2", new Float[]{1.0f, 2.0f},
                "date", now
        );

        var document = Document.builder().with(fields).build();
        assertEquals(1, document.field("integer").orElseThrow().value());
        assertEquals("a string", document.field("string").orElseThrow().value());
        assertEquals(now.toString(), document.field("date").orElseThrow().value());

        var vector1 = document.field("vector1").orElseThrow().value();
        assertInstanceOf(float[].class, vector1);
        assertArrayEquals(new float[]{1.0f, 2.0f}, (float[]) vector1);
        var vector2 = document.field("vector2").orElseThrow().value();
        assertInstanceOf(float[].class, vector2);
        assertArrayEquals(new float[]{1.0f, 2.0f}, (float[]) vector2);
    }

}