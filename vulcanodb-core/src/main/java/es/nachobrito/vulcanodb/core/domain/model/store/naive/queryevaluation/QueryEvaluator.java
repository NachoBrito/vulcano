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

package es.nachobrito.vulcanodb.core.domain.model.store.naive.queryevaluation;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;
import es.nachobrito.vulcanodb.core.domain.model.result.ResultDocument;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * @author nacho
 */
public interface QueryEvaluator {

    record Candidate(Document document, float score) {
        ResultDocument toResult() {
            return new ResultDocument(document, score);
        }
    }

    Function<Document, Candidate> mapper();

    Predicate<Candidate> predicate();

    Collector<Candidate, ResultBuilder, Result> collector();

    Comparator<Candidate> comparator();

    static QueryEvaluator of(Query query) {
        return new DefaultQueryEvaluator(query);
    }
}
