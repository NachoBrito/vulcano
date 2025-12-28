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

package es.nachobrito.vulcanodb.core.store.axon.index.hnsw;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import es.nachobrito.vulcanodb.core.Embedding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
public class HnswIndexSearchEmbeddingsTest {

    @Test
    void expectSearchWorks() {
        var embeddingModel = Embedding.MODEL;
        var config = HnswConfig
                .builder()
                .withDimensions(embeddingModel.dimension())
                .withEfConstruction(500) // max recall
                .withEfSearch(500) // max recall
                .build();
        var index = new HnswIndex(config);
        var query = "how word embeddings work";
        var queryVector = embeddingModel.embed(query).content().vector();
        var similarities = new HashMap<Long, Float>();
        indexData(index, embeddingModel, queryVector, similarities);
        final var sortedSimilarities = sortSimilarities(similarities);

        var results = index.search(queryVector, 5);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.size() == 5);

        int i = 0;
        for (var entry : sortedSimilarities.entrySet()) {
            if (i >= results.size()) {
                break;
            }
            assertEquals(entry.getKey(), results.get(i).vectorId());
            assertEquals(entry.getValue(), results.get(i).similarity());
            i++;
        }
    }

    private static LinkedHashMap<Long, Float> sortSimilarities(HashMap<Long, Float> similarities) {
        return similarities
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Long, Float>comparingByValue().reversed())
                .collect(Collectors
                        .toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private void indexData(HnswIndex index, EmbeddingModel embeddingModel, float[] queryVector, HashMap<Long, Float> similarities) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream("test-data/data-science-interview-qa.csv")) {
            try (CSVReader csvReader = new CSVReader(new InputStreamReader(is))) {
                String[] values = null;
                while ((values = csvReader.readNext()) != null) {
                    var answer = values[1];
                    var embedding = embeddingModel.embed(answer).content().vector();
                    var id = index.insert(embedding);
                    var similarity = index.getConfig().vectorSimilarity().between(embedding, queryVector);
                    similarities.put(id, similarity);
                }
            } catch (CsvValidationException | IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
