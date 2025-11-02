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

package es.nachobrito.vulcanodb.core.domain.model.store.queryevaluation;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;

import java.util.Set;
import java.util.function.*;
import java.util.stream.Collector;

/**
 * @author nacho
 */
public class DefaultQueryEvaluator implements QueryEvaluator {
    private static final double MIN_SCORE = .0;

    private final Query query;

    public DefaultQueryEvaluator(Query query) {
        this.query = query;
    }

    @Override
    public Function<Document, Candidate> mapper() {
        return (Document document) -> new Candidate(document, query.apply(document));
    }

    @Override
    public Predicate<Candidate> predicate() {
        return (Candidate candidate) -> candidate.score() > MIN_SCORE;
    }

    @Override
    public Collector<Candidate, ResultBuilder, Result> collector() {
        return new Collector<>() {
            @Override
            public Supplier<ResultBuilder> supplier() {
                return ResultBuilder::new;
            }

            @Override
            public BiConsumer<ResultBuilder, Candidate> accumulator() {
                return (builder, candidate) -> {
                    builder.addDocument(candidate.toResult());
                };
            }

            @Override
            public BinaryOperator<ResultBuilder> combiner() {
                return ResultBuilder::combine;
            }

            @Override
            public Function<ResultBuilder, Result> finisher() {
                return ResultBuilder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        };

    }
}
