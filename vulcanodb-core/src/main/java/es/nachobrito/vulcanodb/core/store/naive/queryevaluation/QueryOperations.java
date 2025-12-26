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

package es.nachobrito.vulcanodb.core.store.naive.queryevaluation;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.query.Query;
import es.nachobrito.vulcanodb.core.result.QueryResult;
import es.nachobrito.vulcanodb.core.result.QueryResultBuilder;
import es.nachobrito.vulcanodb.core.result.ResultDocument;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * @author nacho
 */
public interface QueryOperations {

    record Candidate(Document document, float score) {
        ResultDocument toResult() {
            return new ResultDocument(document, score);
        }
    }

    Function<Document, Candidate> mapper();

    Predicate<Candidate> predicate();

    Collector<Candidate, QueryResultBuilder, QueryResult> collector();

    Comparator<Candidate> comparator();

    static QueryOperations of(Query query) {
        return new DefaultQueryOperations(query);
    }
}
