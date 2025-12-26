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

import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.DocIdSet;
import es.nachobrito.vulcanodb.core.store.axon.queryevaluation.ExecutionContext;

import java.util.Comparator;
import java.util.List;

/**
 * @author nacho
 */
public record AndNode(List<BitmapOperator> children) implements BitmapOperator {

    @Override
    public DocIdSet compute(ExecutionContext ctx) {
        // Optimization: Sort children by cost!
        // Execute the cheapest/most selective filter first.
        children.sort(Comparator.comparingDouble(BitmapOperator::estimateCost));

        DocIdSet result = children.getFirst().compute(ctx);
        for (int i = 1; i < children.size(); i++) {
            // Intersect results
            result.and(children.get(i).compute(ctx));

            // Short-circuit: If result is empty, stop immediately
            if (result.isEmpty()) return result;
        }
        return result;
    }
}
