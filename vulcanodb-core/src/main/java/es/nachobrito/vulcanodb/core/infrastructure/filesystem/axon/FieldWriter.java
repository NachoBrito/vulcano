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

import es.nachobrito.vulcanodb.core.domain.model.document.DocumentId;
import es.nachobrito.vulcanodb.core.domain.model.document.Field;
import es.nachobrito.vulcanodb.core.util.FileNameHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * @author nacho
 */
public class FieldWriter {
    private final Path dataFolder;

    public FieldWriter(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public Callable<Void> writeOperation(DocumentId documentId, Field<?, ?> field) {
        return () -> {
            Path destFile = getDestinationFile(field);
            byte[] data = getBinaryRepresentation(documentId, field);
            Set<OpenOption> options = new HashSet<OpenOption>();
            options.add(APPEND);
            options.add(CREATE);
            Set<PosixFilePermission> perms =
                    PosixFilePermissions.fromString("rw-r-----");
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(perms);

            try (var sbc =
                         Files.newByteChannel(destFile, options, attr)) {
                ByteBuffer bb = ByteBuffer.wrap(data);
                sbc.write(bb);
            } catch (IOException x) {
                IO.println("Exception thrown: " + x);
            }
            return null;
        };

    }

    private byte[] getBinaryRepresentation(DocumentId documentId, Field<?, ?> field) {
        var string = documentId.value().toString() + ":" + field.value().toString();
        return string.getBytes(StandardCharsets.UTF_8);
    }

    private Path getDestinationFile(Field<?, ?> field) {
        var folder = FileNameHelper.toLegalFileName(field.key());
        var parent = dataFolder.resolve(folder);
        parent.toFile().mkdirs();
        return parent.resolve(field.type().getSimpleName() + ".vulcano");
    }
}
