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


import java.util.function.Supplier;

/**
 * Core interface for database telemetry and metrics collection.
 * <p>
 * This interface is designed for injection into performance-critical components such as indexers
 * and searchers. Implementations should minimize overhead to ensure that telemetry collection
 * does not significantly impact database performance.
 *
 * @author nacho
 */
public interface Telemetry extends AutoCloseable {

    /**
     * Checks if metrics collection is globally enabled.
     * <p>
     * Callers should check this before performing expensive calculations required only for metrics.
     *
     * @return {@code true} if telemetry is enabled, {@code false} otherwise.
     */
    boolean isEnabled();

    /**
     * Determines if a metric at the specified importance level should be captured.
     *
     * @param level the importance level of the metric
     * @return {@code true} if metrics at this level should be captured, {@code false} otherwise.
     */
    boolean shouldCapture(MetricLevel level);

    /**
     * Returns the current sampling rate used for metrics collection.
     *
     * @return the current {@link SamplingRate}
     */
    default SamplingRate getSamplingRate() {
        return SamplingRate.MEDIUM;
    }

    /**
     * Determines if a specific metric should be sampled based on its name and the current sampling configuration.
     * <p>
     * High-frequency metrics may be sampled rather than recorded on every occurrence to reduce overhead.
     * This is a hint for callers; recording methods like {@link #recordTimer(MetricName, long)}
     * will always process the metric if explicitly invoked.
     * <p>
     * The default implementation uses {@link #getSamplingRate()} to decide whether to sample
     * the metric, regardless of its name.
     *
     * @param name the name of the metric
     * @return {@code true} if the metric should be recorded, {@code false} if it should be skipped
     */
    default boolean shouldCapture(MetricName name) {
        return getSamplingRate().shouldSample();
    }

    // --- Counters (Monotonically increasing values) ---

    /**
     * Increments the specified counter by 1.
     *
     * @param name the name of the counter to increment
     */
    void incrementCounter(MetricName name);

    /**
     * Increments the specified counter by the given amount.
     *
     * @param name   the name of the counter to increment
     * @param amount the amount to add to the counter
     */
    void incrementCounter(MetricName name, long amount);

    // --- Timers (Latencies and Durations) ---

    /**
     * Records a duration for the specified metric.
     * <p>
     * For performance-critical code paths, use this method directly with a manually calculated
     * duration to avoid the object allocations associated with lambda-based timing patterns.
     *
     * @param name          the name of the timer metric
     * @param durationNanos the duration to record, in nanoseconds
     */
    void recordTimer(MetricName name, long durationNanos);

    /**
     * Provides a convenient way to measure a block of code using the try-with-resources pattern.
     * <p>
     * Note: This approach involves a small allocation for the returned {@link AutoCloseable}.
     *
     * @param name the name of the timer metric
     * @return an {@link AutoCloseable} that records the duration upon closing
     */
    default AutoCloseable time(MetricName name) {
        long start = System.nanoTime();
        return () -> recordTimer(name, System.nanoTime() - start);
    }

    // --- Gauges (Instantaneous values) ---

    /**
     * Registers a gauge that tracks a value provided by the given supplier.
     * <p>
     * Gauges are useful for tracking instantaneous values like memory usage, cache sizes,
     * or queue depths.
     *
     * @param name          the name of the gauge
     * @param valueSupplier the supplier providing the current value of the gauge
     */
    void registerGauge(MetricName name, Supplier<Number> valueSupplier);
}
