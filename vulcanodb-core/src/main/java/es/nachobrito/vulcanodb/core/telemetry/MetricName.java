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

package es.nachobrito.vulcanodb.core.telemetry;

/**
 * @author nacho
 */
public enum MetricName {
    SEARCH_LATENCY("vucanodb.search.latency"),
    SEARCH_COUNT("vucanodb.search.count"),
    DOCUMENT_INSERT_COUNT("vulcanodb.document.inserts"),
    DOCUMENT_INSERT_LATENCY("vulcanodb.document.insert.latency"),
    DOCUMENT_REMOVE_COUNT("vulcanodb.document.removals"),
    DOCUMENT_REMOVE_LATENCY("vulcanodb.document.removal.latency"),

    OFF_HEAP_MEMORY_USAGE("vulcanodb.memory.offheap"),
    INDEX_RECALL_ESTIMATE("vulcanodb.index.recall"),
    HNSW_DISTANCE_CALCULATIONS("vulcanodb.hnsw.distance.calcs");

    private final String key;

    MetricName(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
