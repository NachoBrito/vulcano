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

package es.nachobrito.vulcanodb.examples.rag.infrastructure.langchain4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.jlama.JlamaStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.BaseRagService;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.RagQuery;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.RagTokens;
import es.nachobrito.vulcanodb.langchain4j.VulcanoDbEmbeddingStore;
import io.micronaut.context.BeanLocator;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;

/**
 * @author nacho
 */
@Singleton
public class LangChain4JRagService extends BaseRagService {

    // expect a more focused and deterministic answer
    public static final float TEMPERATURE = 0.2f;
    private static final String CHAT_MODEL = "tjake/Llama-3.2-1B-Instruct-JQ4";

    private static final Logger log = LoggerFactory.getLogger(LangChain4JRagService.class);
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final PromptTemplate promptTemplate =
            PromptTemplate.from(
                    "Context information is below.:\n"
                            + "------------------\n"
                            + "{{information}}\n"
                            + "------------------\n"
                            + "Given the context information and not prior knowledge, answer the query.\n"
                            + "Query: {{question}}\n"
                            + "Answer:");

    private EmbeddingModel embeddingModel;
    private StreamingChatModel chatModel;

    public LangChain4JRagService(
            BeanLocator beanLocator,
            VulcanoDb vulcanoDb) {
        super(beanLocator);
        getEmbeddingModel();
        getChatModel();
        embeddingStore = new VulcanoDbEmbeddingStore(vulcanoDb);
    }


    private EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            log.info("Loading embedding model");
            embeddingModel = new BgeSmallEnV15EmbeddingModel();
        }
        return embeddingModel;
    }

    private StreamingChatModel getChatModel() {
        if (chatModel == null) {
            log.info("Loading chat model: {}", CHAT_MODEL);
            chatModel =
                    JlamaStreamingChatModel
                            .builder()
                            .modelName(CHAT_MODEL)
                            .temperature(TEMPERATURE).build();
        }
        return chatModel;
    }


    @Override
    public void chat(
            RagQuery query,
            Consumer<RagTokens> ragTokensConsumer,
            Consumer<EmbeddingMatch<TextSegment>> onEmbeddingMatch) {
        Prompt prompt = buildPrompt(query, onEmbeddingMatch);
        var userMessage = prompt.text();
        log.info("Generated prompt\n{}", userMessage);
        getChatModel()
                .chat(
                        userMessage,
                        new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String s) {
                                ragTokensConsumer.accept(RagTokens.partialResponse(query.uuid(), s));
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse chatResponse) {
                                log.info("Response complete: {}", chatResponse.aiMessage().text());
                                ragTokensConsumer.accept(RagTokens.completeResponse(query.uuid()));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                log.error(throwable.getMessage(), throwable);
                            }
                        });
    }

    @Override
    public float[] embed(String text) {
        return this.getEmbeddingModel().embed(text).content().vector();
    }

    private Prompt buildPrompt(RagQuery query, Consumer<EmbeddingMatch<TextSegment>> onEmbeddingMatch) {
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings = getRelevantEmbeddings(query);
        if (relevantEmbeddings.isEmpty()) {
            log.info(
                    "Could not find relevant documents for this query. Sending raw prompt to the model.");
            return Prompt.from(query.text());
        }

        relevantEmbeddings.forEach(onEmbeddingMatch);
        var mapper = new TextEmbeddingMatchMapper();
        String information =
                relevantEmbeddings.stream()
                        .map(mapper)
                        .distinct()
                        .collect(joining("\n\n"));

        Map<String, Object> promptInputs = new HashMap<>();
        promptInputs.put("question", query.text());
        promptInputs.put("information", information);

        return promptTemplate.apply(promptInputs);
    }

    private List<EmbeddingMatch<TextSegment>> getRelevantEmbeddings(RagQuery query) {
        Embedding questionEmbedding = getEmbeddingModel().embed(query.text()).content();
        EmbeddingSearchRequest embeddingSearchRequest =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(2)
                        .minScore(0.85)
                        .build();
        return embeddingStore.search(embeddingSearchRequest).matches();
    }
}
