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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Set;
import java.util.function.*;
import java.util.stream.Collector;

/**
 * @author nacho
 */
public class DefaultQueryOperations implements QueryOperations {
    private static final float MIN_SCORE = .0f;
    private static final CandiatePredicate CANDIDATE_PREDICATE = new CandiatePredicate();
    private static final CandidateCollector CANDIDATE_COLLECTOR = new CandidateCollector();
    private static final CandidateComparator CANDIDATE_COMPARATOR = new CandidateComparator();
    private static final QueryEvaluator queryEvaluator = new QueryEvaluator();

    private final CandidateMapper candidateMapper;

    public DefaultQueryOperations(Query query) {
        candidateMapper = new CandidateMapper(query);
    }

    @Override
    public Function<Document, Candidate> mapper() {
        return candidateMapper;
    }

    @Override
    public Predicate<Candidate> predicate() {
        return CANDIDATE_PREDICATE;
    }

    @Override
    public Collector<Candidate, QueryResultBuilder, QueryResult> collector() {
        return CANDIDATE_COLLECTOR;
    }

    @Override
    public Comparator<Candidate> comparator() {
        return CANDIDATE_COMPARATOR;
    }

    static class CandidateMapper implements Function<Document, QueryOperations.Candidate> {

        private final Query query;

        public CandidateMapper(Query query) {
            this.query = query;
        }

        @Override
        public QueryOperations.Candidate apply(Document document) {
            var score = queryEvaluator.apply(query, document);
            if (score > MIN_SCORE) {
                return new QueryOperations.Candidate(document, score);
            }
            return null;
        }
    }

    static class CandiatePredicate implements Predicate<Candidate> {

        @Override
        public boolean test(Candidate candidate) {
            return candidate != null;
        }
    }

    static class CandidateCollector implements Collector<Candidate, QueryResultBuilder, QueryResult> {
        @Override
        public Supplier<QueryResultBuilder> supplier() {
            return QueryResult::builder;
        }

        @Override
        public BiConsumer<QueryResultBuilder, Candidate> accumulator() {
            return (builder, candidate) -> {
                builder.addDocument(candidate.toResult());
            };
        }

        @Override
        public BinaryOperator<QueryResultBuilder> combiner() {
            return QueryResultBuilder::combine;
        }

        @Override
        public Function<QueryResultBuilder, QueryResult> finisher() {
            return QueryResultBuilder::build;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }

    static class CandidateComparator implements Comparator<Candidate>, Serializable {

        @Override
        public int compare(Candidate o1, Candidate o2) {
            return Float.compare(o2.score(), o1.score());
        }
    }
}
