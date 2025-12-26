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

package es.nachobrito.vulcanodb.core.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class DocumentTest {

    @Test
    void expectToMapReturnsUnmodifiableMap() {
        var documentMap = Document.builder()
                .withStringField("StringField", "StringValue")
                .withIntegerField("IntegerField", 100)
                .withVectorField("VectorField", new float[]{1.0f, 2.0f})
                .build()
                .toMap();

        assertThrows(UnsupportedOperationException.class, () -> {
            documentMap.put("New value", "Should fail");
        });

        assertEquals("StringValue", documentMap.get("StringField"));
        assertEquals(100, documentMap.get("IntegerField"));
        assertArrayEquals(new float[]{1.0f, 2.0f}, (float[]) documentMap.get("VectorField"));
    }
}