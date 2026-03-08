/*
 *    Copyright 2026 Nacho Brito
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

package es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana.buffer;

import java.nio.ByteBuffer;

/**
 * A decorator for {@link TucanaBuffer} that applies a fixed base offset to all operations.
 */
public class BoundedTucanaBuffer implements TucanaBuffer {

    private final TucanaBuffer delegate;
    private final long baseOffset;

    public BoundedTucanaBuffer(TucanaBuffer delegate, long baseOffset) {
        this.delegate = delegate;
        this.baseOffset = baseOffset;
    }

    @Override
    public byte getByte(long offset) {
        return delegate.getByte(baseOffset + offset);
    }

    @Override
    public void putByte(long offset, byte value) {
        delegate.putByte(baseOffset + offset, value);
    }

    @Override
    public int getInt(long offset) {
        return delegate.getInt(baseOffset + offset);
    }

    @Override
    public void putInt(long offset, int value) {
        delegate.putInt(baseOffset + offset, value);
    }

    @Override
    public long getLong(long offset) {
        return delegate.getLong(baseOffset + offset);
    }

    @Override
    public void putLong(long offset, long value) {
        delegate.putLong(baseOffset + offset, value);
    }

    @Override
    public ByteBuffer getBytes(long offset, int length) {
        return delegate.getBytes(baseOffset + offset, length);
    }

    @Override
    public void putBuffer(long offset, ByteBuffer buffer) {
        delegate.putBuffer(baseOffset + offset, buffer);
    }

    @Override
    public long offset() {
        return baseOffset;
    }

    @Override
    public void force() {
        delegate.force();
    }
}
