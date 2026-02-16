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

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulcanoDbEmbeddingStoreTest {

    private VulcanoDb vulcanoDb;
    private VulcanoDbEmbeddingStore embedStore;

    @BeforeEach
    void setUp() {
        vulcanoDb = VulcanoDb.builder().build();
        embedStore = new VulcanoDbEmbeddingStore(vulcanoDb);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (vulcanoDb != null) {
            vulcanoDb.close();
        }
    }

    @Test
    void should_add_embedding() {
        Embedding embedding = Embedding.from(new float[]{1.0f, 2.0f});

        String id = embedStore.add(embedding);

        assertThat(id).isNotNull();

        // Verify via search
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().getFirst().embeddingId()).isEqualTo(id);
    }

    @Test
    void should_add_embedding_with_id() {
        String id = "test-id";
        Embedding embedding = Embedding.from(new float[]{1.0f, 2.0f});

        embedStore.add(id, embedding);

        // Verify via search
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().getFirst().embeddingId()).isEqualTo(id);
    }

    @Test
    void should_add_embedding_with_text_segment() {
        Embedding embedding = Embedding.from(new float[]{1.0f, 2.0f});
        TextSegment textSegment = TextSegment.from("hello world", Metadata.from("key", "value"));

        String id = embedStore.add(embedding, textSegment);

        assertThat(id).isNotNull();

        // Verify via search
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = result.matches().getFirst();
        assertThat(match.embedded().text()).isEqualTo("hello world");
        assertThat(match.embedded().metadata().getString("key")).isEqualTo("value");
    }

    @Test
    void should_add_all_embeddings() {
        List<Embedding> embeddings = List.of(
                Embedding.from(new float[]{1.0f, 0.0f}),
                Embedding.from(new float[]{0.0f, 1.0f})
        );

        List<String> ids = embedStore.addAll(embeddings);

        assertThat(ids).hasSize(2);

        // Verify both are present (using a broad search)
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.5f, 0.5f}))
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).hasSize(2);
    }

    @Test
    void should_remove_embedding() {
        Embedding embedding = Embedding.from(new float[]{1.0f, 2.0f});
        String id = embedStore.add(embedding);

        embedStore.remove(id);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_remove_all_embeddings() {
        Embedding e1 = Embedding.from(new float[]{1.0f, 0.0f});
        Embedding e2 = Embedding.from(new float[]{0.0f, 1.0f});
        List<String> ids = embedStore.addAll(List.of(e1, e2));

        embedStore.removeAll(ids);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.5f, 0.5f}))
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> result = embedStore.search(searchRequest);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_filter_by_min_score() {
        // In NaiveInMemoryDataStore, the score is likely cosine similarity or similar
        Embedding e1 = Embedding.from(new float[]{1.0f, 0.0f}); // Perfect match
        Embedding e2 = Embedding.from(new float[]{0.0f, 1.0f}); // Orthogonal (low score)

        embedStore.add(e1);
        embedStore.add(e2);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(e1)
                .maxResults(10)
                .minScore(0.9)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embedStore.search(searchRequest);

        assertThat(searchResult.matches()).hasSize(1);
    }

    @Test
    void should_handle_various_metadata_types() {
        Metadata metadata = new Metadata();
        metadata.put("string", "value");
        metadata.put("integer", 1);
        metadata.put("long", 2L);
        metadata.put("float", 3.0f);
        metadata.put("double", 4.0);
        UUID uuid = UUID.randomUUID();
        metadata.put("uuid", uuid);

        TextSegment segment = TextSegment.from("text", metadata);
        Embedding embedding = Embedding.from(new float[]{1.0f});

        String id = embedStore.add(embedding, segment);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embedStore.search(searchRequest);

        Metadata resultMetadata = searchResult.matches().get(0).embedded().metadata();
        assertThat(resultMetadata.getString("string")).isEqualTo("value");
        assertThat(resultMetadata.getInteger("integer")).isEqualTo(1);
        assertThat(resultMetadata.getLong("long")).isEqualTo(2L);
        assertThat(resultMetadata.getFloat("float")).isEqualTo(3.0f);
        assertThat(resultMetadata.getDouble("double")).isEqualTo(4.0);
        assertThat(resultMetadata.getUUID("uuid")).isEqualTo(uuid);
    }

    @Test
    void should_throw_exception_when_adding_all_with_different_sizes() {
        List<String> ids = List.of("1");
        List<Embedding> embeddings = List.of(Embedding.from(new float[]{1.0f}), Embedding.from(new float[]{2.0f}));
        List<TextSegment> segments = List.of(TextSegment.from("text"));

        assertThrows(IllegalArgumentException.class, () -> embedStore.addAll(ids, embeddings, segments));
        assertThrows(IllegalArgumentException.class, () -> embedStore.addAll(embeddings, segments));
    }
}
