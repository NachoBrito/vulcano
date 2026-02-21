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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.appendonly;

/**
 * @author nacho
 */
enum ValueType {
    STRING(1),
    INTEGER(2),
    FLOAT_ARRAY(3),
    FLOAT_MATRIX(4),
    BYTES(5);

    final int id;

    ValueType(int id) {
        this.id = id;
    }

    static ValueType fromId(int id) {
        return switch (id) {
            case 1 -> STRING;
            case 2 -> INTEGER;
            case 3 -> FLOAT_ARRAY;
            case 4 -> FLOAT_MATRIX;
            case 5 -> BYTES;
            default -> throw new IllegalStateException("Unknown type " + id);
        };
    }
}
