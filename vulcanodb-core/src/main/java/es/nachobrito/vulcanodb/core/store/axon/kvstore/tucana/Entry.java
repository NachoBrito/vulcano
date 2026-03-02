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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Provides utility methods for serializing and deserializing key-value pairs into {@link ByteBuffer} instances.
 * <p>
 * Each entry is stored using a structured byte layout:
 * <ol>
 *     <li><b>Key Length</b> (4 bytes): The length of the key in bytes.</li>
 *     <li><b>Value Length</b> (4 bytes): The length of the value in bytes.</li>
 *     <li><b>Value Data Type</b> (1 byte): An identifier for the type of data stored.</li>
 *     <li><b>Dimension #1</b> (4 bytes): Used for array and matrix types to store size information.</li>
 *     <li><b>Dimension #2</b> (4 bytes): Used for matrix types to store the second dimension size.</li>
 *     <li><b>Key Bytes</b>: The UTF-8 encoded bytes of the key string.</li>
 *     <li><b>Value Bytes</b>: The binary representation of the value.</li>
 * </ol>
 *
 * @author nacho
 */
public interface Entry {

    /**
     * The total length of the header in bytes.
     */
    int HEADER_LENGTH = 1 + 4 * Integer.BYTES;

    /**
     * The buffer offset where the key length is stored.
     */
    int HEADER_KEY_LENGTH_INDEX = 0;

    /**
     * The buffer offset where the value length is stored.
     */
    int HEADER_VALUE_LENGTH_INDEX = 4;

    /**
     * The buffer offset where the data type identifier is stored.
     */
    int HEADER_VALUE_DATATYPE_INDEX = 8;

    /**
     * The buffer offset where the first dimension size is stored.
     */
    int HEADER_DIMENSION1_INDEX = 9;

    /**
     * The buffer offset where the second dimension size is stored.
     */
    int HEADER_DIMENSION2_INDEX = 13;

    /**
     * Creates a {@link ByteBuffer} containing an entry with a {@code String} key and an {@code int} value.
     *
     * @param key   the key for the entry
     * @param value the integer value for the entry
     * @return a buffer containing the serialized entry
     */
    static ByteBuffer of(String key, int value) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var keyLength = keyBytes.length;
        var valueLength = Integer.BYTES;
        var buffer = ByteBuffer.allocate(HEADER_LENGTH + keyLength + valueLength);
        buffer.putInt(keyLength);
        buffer.putInt(valueLength);
        buffer.put(Type.INTEGER.value);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.put(keyBytes);
        buffer.putInt(value);
        return buffer;
    }


    /**
     * Reads the integer value from the provided entry buffer.
     *
     * @param buffer the buffer containing the serialized entry
     * @return the integer value stored in the entry
     * @throws IllegalStateException if the entry data type is not {@link Type#INTEGER}
     */
    static int readIntValue(ByteBuffer buffer) {
        Type.INTEGER.validate(buffer);
        int keyLength = buffer.getInt(HEADER_KEY_LENGTH_INDEX);
        return buffer.getInt(HEADER_LENGTH + keyLength);
    }

    /**
     * Creates a {@link ByteBuffer} containing an entry with a {@code String} key and a {@code String} value.
     *
     * @param key   the key for the entry
     * @param value the string value for the entry
     * @return a buffer containing the serialized entry
     */
    static ByteBuffer of(String key, String value) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var valueBytes = value.getBytes(StandardCharsets.UTF_8);
        var keyLength = keyBytes.length;
        var valueLength = valueBytes.length;
        var buffer = ByteBuffer.allocate(HEADER_LENGTH + keyLength + valueLength);
        buffer.putInt(keyLength);
        buffer.putInt(valueLength);
        buffer.put(Type.STRING.value);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.put(keyBytes);
        buffer.put(valueBytes);
        return buffer;
    }


    /**
     * Reads the string value from the provided entry buffer.
     *
     * @param buffer the buffer containing the serialized entry
     * @return the string value stored in the entry, decoded using UTF-8
     * @throws IllegalStateException if the entry data type is not {@link Type#STRING}
     */
    static String readStringValue(ByteBuffer buffer) {
        Type.STRING.validate(buffer);
        int keyLength = buffer.getInt(HEADER_KEY_LENGTH_INDEX);
        var valueLength = buffer.getInt(Integer.BYTES);
        var valueBytes = new byte[valueLength];
        buffer.get(HEADER_LENGTH + keyLength, valueBytes);
        return new String(valueBytes, StandardCharsets.UTF_8);
    }

    /**
     * Creates a {@link ByteBuffer} containing an entry with a {@code String} key and a {@code float} value.
     *
     * @param key   the key for the entry
     * @param value the float value for the entry
     * @return a buffer containing the serialized entry
     */
    static ByteBuffer of(String key, float value) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var keyLength = keyBytes.length;
        var valueLength = Float.BYTES;
        var buffer = ByteBuffer.allocate(HEADER_LENGTH + keyLength + valueLength);
        buffer.putInt(keyLength);
        buffer.putInt(valueLength);
        buffer.put(Type.FLOAT.value);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.put(keyBytes);
        buffer.putFloat(value);
        return buffer;
    }

    /**
     * Reads the float value from the provided entry buffer.
     *
     * @param entry the buffer containing the serialized entry
     * @return the float value stored in the entry
     * @throws IllegalStateException if the entry data type is not {@link Type#FLOAT}
     */
    static float readFloatValue(ByteBuffer entry) {
        Type.FLOAT.validate(entry);
        int keyLength = entry.getInt(HEADER_KEY_LENGTH_INDEX);
        return entry.getFloat(HEADER_LENGTH + keyLength);
    }

    /**
     * Creates a {@link ByteBuffer} containing an entry with a {@code String} key and a {@code float[]} value.
     *
     * @param key   the key for the entry
     * @param value the float array for the entry
     * @return a buffer containing the serialized entry
     */
    static ByteBuffer of(String key, float[] value) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var keyLength = keyBytes.length;
        var valueLength = value.length * Float.BYTES;
        var buffer = ByteBuffer.allocate(HEADER_LENGTH + keyLength + valueLength);
        buffer.putInt(keyLength);
        buffer.putInt(valueLength);
        buffer.put(Type.FLOAT_ARRAY.value);
        buffer.putInt(value.length);
        buffer.putInt(0);
        buffer.put(keyBytes);
        buffer.asFloatBuffer().put(value);
        return buffer;
    }

    /**
     * Reads the float array value from the provided entry buffer.
     *
     * @param entry the buffer containing the serialized entry
     * @return the float array stored in the entry
     * @throws IllegalStateException if the entry data type is not {@link Type#FLOAT_ARRAY}
     */
    static float[] readFloatArrayValue(ByteBuffer entry) {
        Type.FLOAT_ARRAY.validate(entry);
        int keyLength = entry.getInt(HEADER_KEY_LENGTH_INDEX);
        var valueLength = entry.getInt(Integer.BYTES);
        var value = new float[valueLength / Float.BYTES];
        entry.slice(HEADER_LENGTH + keyLength, valueLength).asFloatBuffer().get(value);
        return value;
    }

    /**
     * Creates a {@link ByteBuffer} containing an entry with a {@code String} key and a {@code float[][]} value (matrix).
     *
     * @param key   the key for the entry
     * @param value the float matrix for the entry
     * @return a buffer containing the serialized entry
     */
    static ByteBuffer of(String key, float[][] value) {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var keyLength = keyBytes.length;
        var dim1 = value.length;
        var dim2 = dim1 > 0 ? value[0].length : 0;
        var valueLength = dim1 * dim2 * Float.BYTES;

        var buffer = ByteBuffer.allocate(HEADER_LENGTH + keyLength + valueLength);
        buffer.putInt(keyLength);
        buffer.putInt(valueLength);
        buffer.put(Type.FLOAT_MATRIX.value);
        buffer.putInt(dim1);
        buffer.putInt(dim2);
        buffer.put(keyBytes);

        var floatBuffer = buffer.asFloatBuffer();
        for (float[] row : value) {
            floatBuffer.put(row);
        }
        return buffer;
    }

    /**
     * Reads the float matrix value from the provided entry buffer.
     *
     * @param entry the buffer containing the serialized entry
     * @return the float matrix stored in the entry
     * @throws IllegalStateException if the entry data type is not {@link Type#FLOAT_MATRIX}
     */
    static float[][] readFloatMatrixValue(ByteBuffer entry) {
        Type.FLOAT_MATRIX.validate(entry);
        int keyLength = entry.getInt(HEADER_KEY_LENGTH_INDEX);
        var index1 = HEADER_LENGTH - (2 * Integer.BYTES);
        var dim1 = entry.getInt(index1);
        var dim2 = entry.getInt(index1 + Integer.BYTES);

        var value = new float[dim1][dim2];
        var floatBuffer = entry.slice(HEADER_LENGTH + keyLength, dim1 * dim2 * Float.BYTES).asFloatBuffer();
        for (int i = 0; i < dim1; i++) {
            floatBuffer.get(value[i]);
        }
        return value;
    }

    /**
     * Reads the key string from the provided entry buffer.
     *
     * @param buffer the buffer containing the serialized entry
     * @return the key string, decoded using UTF-8
     */
    static String readKey(ByteBuffer buffer) {
        int keyLength = buffer.getInt(HEADER_KEY_LENGTH_INDEX);
        var keyBytes = new byte[keyLength];
        buffer.get(HEADER_LENGTH, keyBytes);
        return new String(keyBytes, StandardCharsets.UTF_8);
    }

    /**
     * Defines the supported data types for entry values.
     */
    enum Type {
        /**
         * A UTF-8 encoded string.
         */
        STRING((byte) 1),
        /**
         * A 32-bit signed integer.
         */
        INTEGER((byte) 2),
        /**
         * A 32-bit single-precision floating-point number.
         */
        FLOAT((byte) 3),
        /**
         * An array of 32-bit floating-point numbers.
         */
        FLOAT_ARRAY((byte) 4),
        /**
         * A two-dimensional matrix of 32-bit floating-point numbers.
         */
        FLOAT_MATRIX((byte) 5);

        /**
         * The byte value representing the data type.
         */
        final byte value;

        Type(byte value) {
            this.value = value;
        }

        /**
         * Returns the {@code Type} corresponding to the given byte value.
         *
         * @param b the byte value of the type
         * @return the matching {@code Type}
         * @throws IllegalArgumentException if the byte value does not match any known type
         */
        static Type of(byte b) {
            return switch (b) {
                case 1 -> STRING;
                case 2 -> INTEGER;
                case 3 -> FLOAT;
                case 4 -> FLOAT_ARRAY;
                case 5 -> FLOAT_MATRIX;
                default -> throw new IllegalArgumentException("Unknown type: " + b);
            };
        }

        /**
         * Validates that the data type of the entry in the buffer matches this type.
         *
         * @param buffer the buffer containing the serialized entry
         * @throws IllegalStateException if the data type in the buffer does not match this type
         */
        public void validate(ByteBuffer buffer) {
            var type = buffer.get(HEADER_VALUE_DATATYPE_INDEX);
            if (type != this.value) {
                throw new IllegalStateException("Invalid type: %s, expected: %s".formatted(of(type).name(), this.name()));
            }
        }
    }
}
