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

package es.nachobrito.vulcanodb.core.store.axon.wal;

import es.nachobrito.vulcanodb.core.document.*;

import java.io.*;

/**
 * Utility for binary serialization of WAL entries.
 */
public class WalSerializer {

    private static final byte TYPE_ADD = 0;
    private static final byte TYPE_REMOVE = 1;

    private static final byte VAL_TYPE_STRING = 1;
    private static final byte VAL_TYPE_INTEGER = 2;
    private static final byte VAL_TYPE_VECTOR = 3;
    private static final byte VAL_TYPE_MATRIX = 4;

    public static byte[] serialize(WalEntry entry) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(entry.txId());
            if (entry.type() == WalEntry.Type.ADD) {
                dos.writeByte(TYPE_ADD);
                Document doc = entry.document().orElseThrow();
                dos.writeUTF(doc.id().toString());

                var fields = doc.getfieldsStream().toList();
                dos.writeInt(fields.size());
                for (var field : fields) {
                    dos.writeUTF(field.key());
                    writeValue(dos, field.content());
                }
            } else {
                dos.writeByte(TYPE_REMOVE);
                dos.writeUTF(entry.documentId().orElseThrow());
            }
        }
        return baos.toByteArray();
    }

    private static void writeValue(DataOutputStream dos, FieldValueType<?> value) throws IOException {
        if (value instanceof StringFieldValue sf) {
            dos.writeByte(VAL_TYPE_STRING);
            dos.writeUTF(sf.value());
        } else if (value instanceof IntegerFieldValue ifv) {
            dos.writeByte(VAL_TYPE_INTEGER);
            dos.writeInt(ifv.value());
        } else if (value instanceof VectorFieldValue vf) {
            dos.writeByte(VAL_TYPE_VECTOR);
            float[] vec = vf.value();
            dos.writeInt(vec.length);
            for (float f : vec) dos.writeFloat(f);
        } else if (value instanceof MatrixFieldValue mf) {
            dos.writeByte(VAL_TYPE_MATRIX);
            float[][] mat = mf.value();
            dos.writeInt(mat.length);
            dos.writeInt(mat.length > 0 ? mat[0].length : 0);
            for (float[] row : mat) {
                for (float f : row) dos.writeFloat(f);
            }
        } else {
            throw new IOException("Unsupported field type for WAL: " + value.getClass());
        }
    }

    public static WalEntry deserialize(byte[] bytes) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            long txId = dis.readLong();
            byte type = dis.readByte();
            if (type == TYPE_ADD) {
                String docId = dis.readUTF();
                DocumentBuilder builder = Document.builder().withId(DocumentId.of(docId));
                int fieldCount = dis.readInt();
                for (int i = 0; i < fieldCount; i++) {
                    String key = dis.readUTF();
                    builder.withField(key, readValue(dis));
                }
                return WalEntry.add(txId, builder.build());
            } else {
                String docId = dis.readUTF();
                return WalEntry.remove(txId, docId);
            }
        }
    }

    private static FieldValueType<?> readValue(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        return switch (type) {
            case VAL_TYPE_STRING -> new StringFieldValue(dis.readUTF());
            case VAL_TYPE_INTEGER -> new IntegerFieldValue(dis.readInt());
            case VAL_TYPE_VECTOR -> {
                int len = dis.readInt();
                float[] vec = new float[len];
                for (int i = 0; i < len; i++) vec[i] = dis.readFloat();
                yield new VectorFieldValue(vec);
            }
            case VAL_TYPE_MATRIX -> {
                int rows = dis.readInt();
                int cols = dis.readInt();
                float[][] mat = new float[rows][cols];
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) mat[i][j] = dis.readFloat();
                }
                yield new MatrixFieldValue(mat);
            }
            default -> throw new IOException("Unknown value type in WAL: " + type);
        };
    }
}
