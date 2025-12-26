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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;

/// Compares queries, making sure they are executed in the right order.
///
/// @author nacho
public class QueryComparator implements Comparator<Query>, Serializable {
    private static final Map<Class<? extends Query>, Integer> PRIORITIES = Map.of(
            StringFieldQuery.class, 1,
            IntegerFieldQuery.class, 1
    );
    private static final int DEFAULT_PRIORITY = 0;

    @Override
    public int compare(Query o1, Query o2) {
        var priority1 = PRIORITIES.getOrDefault(o1.getClass(), DEFAULT_PRIORITY);
        var priority2 = PRIORITIES.getOrDefault(o2.getClass(), DEFAULT_PRIORITY);
        return priority1 - priority2;
    }
}
