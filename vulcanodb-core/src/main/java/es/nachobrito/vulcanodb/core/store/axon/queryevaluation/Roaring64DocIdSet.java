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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation;

import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.stream.LongStream;

/**
 * @author nacho
 */
public class Roaring64DocIdSet implements DocIdSet {

    private final Roaring64Bitmap bitmap;

    public Roaring64DocIdSet() {
        this.bitmap = new Roaring64Bitmap();
    }

    @Override
    public void add(long docId) {
        bitmap.addLong(docId);
    }

    @Override
    public void remove(long docId) {
        bitmap.removeLong(docId);
    }

    @Override
    public boolean contains(long docId) {
        return bitmap.contains(docId);
    }

    @Override
    public boolean isEmpty() {
        return bitmap.isEmpty();
    }

    @Override
    public long getCardinality() {
        return bitmap.getLongCardinality();
    }

    @Override
    public void and(DocIdSet other) {
        if (other instanceof Roaring64DocIdSet) {
            // Fast native AND
            bitmap.and(((Roaring64DocIdSet) other).bitmap);
        } else {
            // Fallback for foreign implementations: iterate and intersect
            // This is slow and should be avoided in the hot path
            Roaring64Bitmap otherBitmap = new Roaring64Bitmap();
            other.stream()
                    .filter(this::contains)
                    .forEach(otherBitmap::addLong);
            this.bitmap.clear();
            this.bitmap.or(otherBitmap);
        }
    }

    @Override
    public void or(DocIdSet other) {
        if (other instanceof Roaring64DocIdSet) {
            bitmap.or(((Roaring64DocIdSet) other).bitmap);
        } else {
            other
                    .stream()
                    .forEach(this::add);
        }
    }

    @Override
    public void andNot(DocIdSet other) {
        if (other instanceof Roaring64DocIdSet) {
            bitmap.andNot(((Roaring64DocIdSet) other).bitmap);
        } else {
            other.stream()
                    .forEach(this::remove);
        }
    }

    @Override
    public LongStream stream() {
        return bitmap.stream();
    }


    @Override
    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            bitmap.serialize(dos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
