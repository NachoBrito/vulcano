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

package es.nachobrito.vulcanodb.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nacho
 */
public record MultiQuery(List<? extends Query> queries, QueryOperator operator) implements Query {
    public MultiQuery(List<? extends Query> queries, QueryOperator operator) {
        if (queries.size() < 2) {
            throw new IllegalArgumentException("Logical operations require at least two operands");
        }
        this.queries = new ArrayList<>(queries);
        this.operator = operator;
    }

    @Override
    public List<? extends Query> queries() {
        return Collections.unmodifiableList(queries);
    }
}
