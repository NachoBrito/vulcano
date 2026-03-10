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
public class PrefixedKeyValueStore implements KeyValueStore {
    private final KeyValueStore delegate;
    private final String prefix;

    public PrefixedKeyValueStore(KeyValueStore delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix + ":";
    }

    @Override
    public long putString(String key, String value) {
        return delegate.putString(prefix + key, value);
    }

    @Override
    public long putString(String key, String value, boolean commit) {
        return delegate.putString(prefix + key, value, commit);
    }

    @Override
    public Optional<String> getString(String key) {
        return delegate.getString(prefix + key);
    }

    @Override
    public long putInt(String key, int value) {
        return delegate.putInt(prefix + key, value);
    }

    @Override
    public long putInt(String key, int value, boolean commit) {
        return delegate.putInt(prefix + key, value, commit);
    }

    @Override
    public Optional<Integer> getInt(String key) {
        return delegate.getInt(prefix + key);
    }

    @Override
    public long putFloatArray(String key, float[] value) {
        return delegate.putFloatArray(prefix + key, value);
    }

    @Override
    public long putFloatArray(String key, float[] value, boolean commit) {
        return delegate.putFloatArray(prefix + key, value, commit);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value) {
        return delegate.putFloatMatrix(prefix + key, value);
    }

    @Override
    public long putFloatMatrix(String key, float[][] value, boolean commit) {
        return delegate.putFloatMatrix(prefix + key, value, commit);
    }

    @Override
    public Optional<float[]> getFloatArray(String key) {
        return delegate.getFloatArray(prefix + key);
    }

    @Override
    public Optional<float[][]> getFloatMatrix(String key) {
        return delegate.getFloatMatrix(prefix + key);
    }

    @Override
    public long putBytes(String key, byte[] value) {
        return delegate.putBytes(prefix + key, value);
    }

    @Override
    public long putBytes(String key, byte[] value, boolean commit) {
        return delegate.putBytes(prefix + key, value, commit);
    }

    @Override
    public void commit() {
        delegate.commit();
    }

    @Override
    public Optional<byte[]> getBytes(String key) {
        return delegate.getBytes(prefix + key);
    }

    @Override
    public void remove(String key) {
        delegate.remove(prefix + key);
    }

    @Override
    public long offHeapBytes() {
        return delegate.offHeapBytes();
    }

    @Override
    public Stream<Long> getOffsetStream() {
        return delegate.getOffsetStream();
    }

    @Override
    public String getStringAt(long offset) {
        return delegate.getStringAt(offset);
    }

    @Override
    public String getKeyAt(long offset) {
        return delegate.getKeyAt(offset).substring(this.prefix.length());
    }

    @Override
    public int getIntAt(long offset) {
        return delegate.getIntAt(offset);
    }

    @Override
    public float[] getFloatArrayAt(long offset) {
        return delegate.getFloatArrayAt(offset);
    }

    @Override
    public float[][] getFloatMatrixAt(long offset) {
        return delegate.getFloatMatrixAt(offset);
    }

    @Override
    public byte[] getBytesAt(long offset) {
        return delegate.getBytesAt(offset);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
