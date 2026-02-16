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

package es.nachobrito.vulcanodb.examples.rag.domain.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import es.nachobrito.vulcanodb.core.ingestion.IngestionResult;
import es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.Dataset;

import java.util.function.Consumer;

/**
 * @author nacho
 */
public interface RagService {

    /**
     * Ingests a full data set
     *
     * @param dataset one of the known datasets
     */
    IngestionResult ingest(Dataset dataset);

    /**
     * Sends a query to the LLM. The response will be streamed through the tokens consumer
     *
     * @param query             the query to send
     * @param ragTokensConsumer the consumer to receive response tokens
     */
    void chat(
            RagQuery query,
            Consumer<RagTokens> ragTokensConsumer,
            Consumer<EmbeddingMatch<TextSegment>> onEmbeddingMatch);

    /**
     *
     * @param text
     * @return
     */
    float[] embed(String text);
}
