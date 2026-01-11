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

package es.nachobrito.vulcanodb;

import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author nacho
 */
public class SentenceEmbeddingTest {

    @Test
    void expectSentenceEmbeddingAndSearch() {
        var embeddingModel = new BgeSmallEnV15EmbeddingModel();
        var question = "We want to avoid insects";
        var candidates =
                List.of(
                        "What is the best time to plant rice?",
                        "How often should I water tomato plants?",
                        "What fertilizer is good for wheat crops?",
                        "How can I control pests on my cotton farm?",
                        "Which crop is best for sandy soil?",
                        "Is there a difference between four-wheel drive and all-wheel drive?");
        var db = VulcanoDb.builder().build();
        for (var candidate : candidates) {
            var embedding = convertFloatsToDoubles(embeddingModel.embed(candidate).content().vector());
            var document = Document.builder()
                    .withStringField("sentence", candidate)
                    .withVectorField("embedding", embedding)
                    .build();
            db.add(document);
        }

        var questionVector = convertFloatsToDoubles(embeddingModel.embed(question).content().vector());
        var query = VulcanoDb.queryBuilder().isSimilarTo(questionVector, "embedding").build();
        var result = db.search(query);

        assertNotNull(result);
        assertEquals(candidates.size(), result.getDocuments().size());
        assertEquals("How can I control pests on my cotton farm?", result.getDocuments().get(0).document().field("sentence").get().value());
        result.getDocuments()
                .forEach(it -> IO.println("[%.2f] %s".formatted(it.score(), it.document().field("sentence").get().value())));

    }

    public static float[] convertFloatsToDoubles(float[] input) {
        if (input == null) {
            return null; // Or throw an exception - your choice
        }
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
}
