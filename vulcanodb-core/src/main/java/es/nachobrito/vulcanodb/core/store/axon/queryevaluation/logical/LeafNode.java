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

package es.nachobrito.vulcanodb.core.store.axon.queryevaluation.logical;

/**
 * @author nacho
 */
public record LeafNode(String fieldName, Operation operator, Object value) implements LogicalNode {

    public LeafNode {
        operator.validateOperand(value);
    }

    public boolean evaluate(Object target) {
        operator.validateOperand(target);
        switch (operator) {
            case INT_EQUALS, STRING_EQUALS -> {
                return target.equals(value);
            }
            case INT_LESS_THAN -> {
                return (Integer) target < (Integer) value;
            }
            case INT_LESS_THAN_EQUAL -> {
                return (Integer) target <= (Integer) value;
            }
            case INT_GREATER_THAN -> {
                return (Integer) target > (Integer) value;
            }
            case INT_GREATER_THAN_EQUAL -> {
                return (Integer) target >= (Integer) value;
            }
            case STRING_STARTS_WITH -> {
                return ((String) target).startsWith((String) value);
            }
            case STRING_ENDS_WITH -> {
                return ((String) target).endsWith((String) value);
            }
            case STRING_CONTAINS -> {
                return ((String) target).contains((String) value);
            }
        }
        return false;
    }
}
