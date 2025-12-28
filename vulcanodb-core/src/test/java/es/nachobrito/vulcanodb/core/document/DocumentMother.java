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

package es.nachobrito.vulcanodb.core.document;

import com.github.javafaker.Faker;
import es.nachobrito.vulcanodb.core.Embedding;

import java.util.List;
import java.util.stream.IntStream;

/**
 * @author nacho
 */
public interface DocumentMother {

    /**
     * Returns a list of _documentCount_ random documents
     *
     * @param shape         the shape of the generated documents
     * @param documentCount the number of documents to create
     * @return the list with the new documents.
     */
    static List<Document> random(DocumentShape shape, int documentCount) {
        var stream = IntStream.range(0, documentCount);
        var cores = Runtime.getRuntime().availableProcessors();
        if (documentCount > 2 * cores) {
            stream = stream.parallel();
        }
        return
                stream
                        .mapToObj(i -> random(shape))
                        .toList();
    }

    /**
     * Returns a document with the given shape, having random values in every field.
     * <ul>
     * <li>Numeric fields will have random ints between 0 and Integer.MAX_VALUE</li>
     * <li>String fields will have randomly generated paragraphs</li>
     * </ul>
     *
     * @param shape the document shape
     * @return the new document
     */
    static Document random(DocumentShape shape) {
        var builder = Document.builder();
        var faker = Faker.instance();
        shape.getFields().forEach((fieldName, fieldType) -> {
            if (fieldType.equals(IntegerFieldValue.class)) {
                builder.withIntegerField(fieldName, faker.number().numberBetween(0, Integer.MAX_VALUE));
                return;
            }

            if (fieldType.equals(StringFieldValue.class)) {
                builder.withStringField(fieldName, faker.lorem().paragraph());
                return;
            }

            if (fieldType.equals(VectorFieldValue.class)) {
                var text = faker.resolve("v_for_vendetta.quotes");
                var vector = Embedding.MODEL.embed(text).content().vector();
                builder
                        .withVectorField(fieldName, vector)
                        .withStringField(fieldName + "_original", text);
                return;
            }
            if (fieldType.equals(MatrixFieldValue.class)) {
                var rows = faker.number().numberBetween(5, 10);
                var cols = faker.number().numberBetween(5, 10);
                var vector = new float[rows][cols];
                for (int i = 0; i < rows; i++) {
                    vector[i] = Embedding.MODEL.embed(faker.lorem().sentence()).content().vector();
                }
                builder.withVectorField(fieldName, vector);
            }
        });
        return builder.build();
    }


}
