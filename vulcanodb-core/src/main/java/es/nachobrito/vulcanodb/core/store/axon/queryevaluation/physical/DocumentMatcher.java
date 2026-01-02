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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation.physical;

import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.ExecutionContext;

/**
 * @author nacho
 */
public interface DocumentMatcher {

    record Score(boolean matches, float score) {
        public static Score of(boolean matches) {
            return new Score(matches, matches ? MATCH_MAX : MATCH_MIN);
        }

        public static Score of(float score) {
            return new Score(score > MATCH_MIN, score);
        }

        public Score negate() {
            return new Score(!matches, MATCH_MAX - score);
        }

        public Score and(Score other) {
            var matches = this.matches && other.matches;
            var score = matches ? (float) Math.sqrt(this.score * other.score) : .0f;
            return new Score(matches, score);
        }

        public Score or(Score other) {
            var matches = this.matches || other.matches;
            var score = matches ? .5f * (this.score + other.score) : .0f;
            return new Score(matches, score);
        }
    }

    float MATCH_MAX = 1.0f;
    float MATCH_MIN = 0.0f;


    /**
     *
     * @param docId the document id
     * @param ctx   the query execution context
     * @return the evaluation result as a Score object
     */
    Score matches(long docId, ExecutionContext ctx);
}
