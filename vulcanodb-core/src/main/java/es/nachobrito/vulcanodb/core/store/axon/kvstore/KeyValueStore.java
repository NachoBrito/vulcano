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

package es.nachobrito.vulcanodb.core.store.axon.kvstore;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author nacho
 */
public interface KeyValueStore extends AutoCloseable {
    /**
     * Saves a string associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getString(String)} or {@link #getStringAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    long putString(String key, String value);

    /**
     * Saves a string associated to the given key.
     *
     * @param key    the key
     * @param value  the value
     * @param commit whether to commit metadata (fsync) after the operation.
     * @return the offset of the new value.
     */
    long putString(String key, String value, boolean commit);

    /**
     * Retrieves the String associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    Optional<String> getString(String key);

    /**
     * Saves an int associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getInt(String)} or {@link #getIntAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    long putInt(String key, int value);

    /**
     * Saves an int associated to the given key.
     *
     * @param key    the key
     * @param value  the value
     * @param commit whether to commit metadata (fsync) after the operation.
     * @return the offset of the new value.
     */
    long putInt(String key, int value, boolean commit);

    /**
     * Retrieves the Integer associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    Optional<Integer> getInt(String key);

    /**
     * Saves a float array associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getFloatArray(String)} or {@link #getFloatArrayAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    long putFloatArray(String key, float[] value);

    /**
     * Saves a float array associated to the given key.
     *
     * @param key    the key
     * @param value  the value
     * @param commit whether to commit metadata (fsync) after the operation.
     * @return the offset of the new value.
     */
    long putFloatArray(String key, float[] value, boolean commit);

    /**
     * Saves a float matrix associated to the given key. The stored value can be retrieved by the provided key, or by the
     * returned offset, using the corresponding {@link #getFloatMatrix(String)} or {@link #getFloatMatrixAt(long)} methods.
     *
     * @param key   the key
     * @param value the value
     * @return the offset of the new value.
     */
    long putFloatMatrix(String key, float[][] value);

    /**
     * Saves a float matrix associated to the given key.
     *
     * @param key    the key
     * @param value  the value
     * @param commit whether to commit metadata (fsync) after the operation.
     * @return the offset of the new value.
     */
    long putFloatMatrix(String key, float[][] value, boolean commit);

    /**
     * Retrieves the float array associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    Optional<float[]> getFloatArray(String key);

    /**
     * Retrieves the float array associated to the given key
     *
     * @param key the key
     * @return the associated value, if it exists.
     */
    Optional<float[][]> getFloatMatrix(String key);

    long putBytes(String key, byte[] value);

    long putBytes(String key, byte[] value, boolean commit);

    /**
     * Commits the current state of the data log and index to metadata (fsync).
     */
    void commit();

    Optional<byte[]> getBytes(String key);

    /**
     * Removes the value associated to the provided key
     *
     * @param key the key to remove.
     */
    void remove(String key);

    long offHeapBytes();

    /**
     * Returns a Stream that provides valid offsets where keys of this store can be found. Values can then be read by
     * passing these offsets to {@link #getStringAt(long)}, {@link #getIntAt(long)}, {@link #getFloatArrayAt(long)},
     * {@link #getFloatMatrixAt(long)}
     *
     * @return a Stream of valid Long offsets.
     */
    Stream<Long> getOffsetStream();

    String getStringAt(long offset);

    String getKeyAt(long offset);

    int getIntAt(long offset);

    float[] getFloatArrayAt(long offset);

    float[][] getFloatMatrixAt(long offset);

    byte[] getBytesAt(long offset);
}
