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

package es.nachobrito.vulcanodb.core.result;

import java.util.PriorityQueue;

/**
 * @author nacho
 */
public class QueryResultBuilder {

    QueryResultBuilder() {
    }

    private final PriorityQueue<ResultDocument> documents = new PriorityQueue<>();

    /**
     * Adds a document to the result collection. Documents will be automatically sorted by score.
     *
     * @param document the document to add.
     * @return this builder
     */
    public QueryResultBuilder addDocument(ResultDocument document) {
        this.documents.add(document);
        return this;
    }

    public QueryResult build() {
        return new DocumentCollection(documents.stream().toList());
    }

    /**
     * Adds every document in the other builder to this builder. Documents will be automatically sorted by score.
     *
     * @param other the other result builder
     * @return this builder
     */
    public QueryResultBuilder combine(QueryResultBuilder other) {
        this.documents.addAll(other.documents);
        return this;
    }

}
