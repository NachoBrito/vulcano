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

package es.nachobrito.vulcano.mcp;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
@MicronautTest(rebuildContext = true)
class DocumentIndexTest {

    @Inject
    DocumentIndex documentIndex;

    @Test
    @Property(name = "vulcano.mcp.index-file", value = "test-data/vulcano-mcp.yaml")
    void expectDataLoaded() {
        List<RelevantFile> paths = documentIndex.getRelevantFiles("roman mythology, flames");
        assertFalse(paths.isEmpty());
        assertTrue(paths.getFirst().path().toString().endsWith("file1.txt"));

        paths = documentIndex.getRelevantFiles("engineering, rows and columns, varchar");
        assertFalse(paths.isEmpty());
        assertTrue(paths.getFirst().path().toString().endsWith("file2.txt"));
    }

}