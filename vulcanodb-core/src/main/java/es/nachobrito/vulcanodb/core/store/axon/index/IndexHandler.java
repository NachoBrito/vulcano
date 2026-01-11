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

package es.nachobrito.vulcanodb.core.store.axon.index;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical.LeafNode;

import java.util.List;

/**
 * @author nacho
 */
public interface IndexHandler<V> extends AutoCloseable {


    void index(Long internalId, Document document);

    List<IndexMatch> search(LeafNode<V> query, int maxResults);

    default List<IndexMatch> search(LeafNode<V> query) {
        return search(query, Integer.MAX_VALUE);
    }
}
