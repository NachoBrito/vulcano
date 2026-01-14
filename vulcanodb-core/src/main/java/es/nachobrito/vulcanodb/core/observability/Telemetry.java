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

package es.nachobrito.vulcanodb.core.observability;


import java.util.function.Supplier;

/**
 * Core interface for database observability.
 * Designed to be injected into hot-path components like Indexers and Searchers.
 *
 * @author nacho
 */
public interface Telemetry {

    /**
     * Check if metrics collection is globally enabled.
     * Components should check this before performing expensive metric calculations.
     */
    boolean isEnabled();

    /**
     * Determines if a specific "verbose" metric should be captured.
     *
     * @param level The importance level of the metric.
     */
    boolean shouldCapture(MetricLevel level);

    // --- Counters (Monotonically increasing values) ---

    void incrementCounter(MetricName name);

    void incrementCounter(MetricName name, long amount);

    // --- Timers (Latencies and Durations) ---

    /**
     * Records a duration. For hot paths, prefer using the start/stop pattern
     * to avoid lambda allocation.
     */
    void recordTimer(MetricName name, long durationNanos);

    /**
     * A helper for the try-with-resources pattern to measure blocks of code.
     */
    default AutoCloseable time(MetricName name) {
        long start = System.nanoTime();
        return () -> recordTimer(name, System.nanoTime() - start);
    }

    // --- Gauges (Instantaneous values) ---

    /**
     * Registers a gauge that tracks a value provided by the supplier.
     * Useful for tracking memory usage or queue depths.
     */
    void registerGauge(MetricName name, Supplier<Number> valueSupplier);
}