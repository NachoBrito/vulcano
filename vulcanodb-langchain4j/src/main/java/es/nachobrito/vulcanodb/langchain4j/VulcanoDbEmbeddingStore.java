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

package es.nachobrito.vulcanodb.langchain4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.nachobrito.vulcanodb.core.VulcanoDb;

import java.util.List;

/**
 * @author nacho
 */
public class VulcanoDbEmbeddingStore<E> implements EmbeddingStore<E> {
    private final VulcanoDb vulcanoDb;

    public VulcanoDbEmbeddingStore(VulcanoDb vulcanoDb) {
        this.vulcanoDb = vulcanoDb;
    }

    @Override
    public String add(Embedding embedding) {
        return "";
    }

    @Override
    public void add(String s, Embedding embedding) {

    }

    @Override
    public String add(Embedding embedding, E e) {
        return "";
    }

    @Override
    public List<String> addAll(List<Embedding> list) {
        return List.of();
    }

    @Override
    public EmbeddingSearchResult<E> search(EmbeddingSearchRequest embeddingSearchRequest) {
        return null;
    }
}
