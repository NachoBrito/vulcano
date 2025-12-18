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

package es.nachobrito.vulcanodb.core.infrastructure.filesystem.axon;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * @author nacho
 */
class DocumentWriterTest {

    @Test
    void expectDocumentWritten() {
        Path dataFolder = Path.of(System.getProperty("user.dir"), ".VulcanoDB");
        var writer = new DefaultDocumentWriter(dataFolder);
        var now = ZonedDateTime.now();
        Map<String, Object> fields = Map.of(
                "integer", 1,
                "string", "a string",
                "vector1", new float[]{1.0f, 2.0f},
                "vector2", new Float[]{1.0f, 2.0f},
                "date", now
        );

        var document = Document.builder().with(fields).build();
        writer.write(document);

    }

}