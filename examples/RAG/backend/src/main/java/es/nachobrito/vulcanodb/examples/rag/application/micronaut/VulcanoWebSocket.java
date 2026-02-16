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

package es.nachobrito.vulcanodb.examples.rag.application.micronaut;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.RagQuery;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.RagService;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.RagTokens;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.Dataset;
import io.micronaut.scheduling.annotation.Async;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nacho
 */
@ServerWebSocket
public class VulcanoWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(VulcanoWebSocket.class);
    private final RagService ragService;

    public VulcanoWebSocket(RagService ragService) {
        this.ragService = ragService;
    }


    @OnOpen
    public void onOpen(WebSocketSession session) {
        log("onOpen", session);
    }

    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        log("onMessage: " + message, session);
        var userMessage = UserMessage.ofJson(message);
        handleMessage(userMessage, session);
    }

    @Async
    protected void handleMessage(UserMessage userMessage, WebSocketSession session) {
        if (userMessage.type().equals(UserMessageType.REINDEX)) {
            var dataSetName = userMessage.payload().get("dataset");
            log("reindex", session);
            var response = new UserMessage(UserMessageType.CHAT, Map.of("response", "Reindexing '%s' dataset.".formatted(dataSetName)));
            session.sendAsync(response);
            var result = ragService.ingest(Dataset.valueOf(userMessage.payload().get("dataset").toUpperCase()));
            var resultMessage = new UserMessage(UserMessageType.CHAT, Map.of("response", "Dataset '%s' was (re)indexed, %d documents processed.".formatted(dataSetName, result.ingestedDocuments())));
            session.sendAsync(resultMessage);
            return;
        }
        var msg = userMessage.payload().get("message");
        var query = RagQuery.of(msg);
        final List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();
        ragService.chat(
                query,
                (tokens) -> {
                    var response = new UserMessage(UserMessageType.CHAT, Map.of("response", tokens.tokens(), "responseId", tokens.queryUuid().toString()));
                    session.sendAsync(response);
                    if (tokens.isComplete() && !embeddingMatches.isEmpty()) {
                        sendDataProvenanceInfo(session, tokens, embeddingMatches);
                    }
                },
                (embeddingMatch) -> {
                    embeddingMatches.add(embeddingMatch);
                    LOG.info("embedding match:");
                    embeddingMatch.embedded().metadata().toMap()
                            .forEach((k, v) -> LOG.info("{}: {}", k, v));
                    var status = "[%d%%] %s, page %s".formatted(
                            Math.round(100 * embeddingMatch.score()),
                            embeddingMatch.embedded().metadata().getString("title"),
                            embeddingMatch.embedded().metadata().getString("page"));
                    var statusMessage = new UserMessage(UserMessageType.STATUS, Map.of("message", status));
                    session.sendAsync(statusMessage);
                });
    }

    private static void sendDataProvenanceInfo(WebSocketSession session, RagTokens tokens, List<EmbeddingMatch<TextSegment>> embeddingMatches) {
        var refMensage = new UserMessage(UserMessageType.CHAT, Map.of("response", "\n\n*RAG References:*\n\n", "responseId", tokens.queryUuid().toString()));
        session.sendAsync(refMensage);
        for (var embeddingMatch : embeddingMatches) {
            var reference = "- [%d%%] %s: [%s](https:%s) (page %s)\n".formatted(
                    Math.round(100 * embeddingMatch.score()),
                    embeddingMatch.embedded().metadata().getString("authors"),
                    embeddingMatch.embedded().metadata().getString("title"),
                    embeddingMatch.embedded().metadata().getString("pdf.url"),
                    embeddingMatch.embedded().metadata().getString("page"));
            refMensage = new UserMessage(UserMessageType.CHAT, Map.of("response", reference, "responseId", tokens.queryUuid().toString()));
            session.sendAsync(refMensage);
        }
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        log("Session closed", session);
    }

    private void log(String event, WebSocketSession session) {
        LOG.info("* WebSocket: {} received for session {}",
                event, session.getId());
    }
}
