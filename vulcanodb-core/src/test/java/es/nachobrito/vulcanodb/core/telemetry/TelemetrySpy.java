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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author nacho
 */
public class TelemetrySpy implements Telemetry {
    Logger logger = LoggerFactory.getLogger(TelemetrySpy.class);

    private final Map<MetricName, Long> counters = new ConcurrentHashMap<>();
    private final Map<MetricName, List<Long>> timers = new ConcurrentHashMap<>();
    private final Map<MetricName, List<Number>> gauges = new ConcurrentHashMap<>();

    private final Map<MetricLevel, Object> shouldCaptureLevelInvocations = new EnumMap<>(MetricLevel.class);
    private final Map<MetricName, Object> shouldCaptureMetricInvocations = new EnumMap<>(MetricName.class);
    private final AtomicInteger isEnabledInvocations = new AtomicInteger(0);

    @Override
    public boolean isEnabled() {
        logger.info("{} -> isEnabled", Thread.currentThread().threadId());
        isEnabledInvocations.incrementAndGet();
        return true;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        logger.info("{} -> shouldCapture(level: {})", Thread.currentThread().threadId(), level);
        ((AtomicInteger) shouldCaptureLevelInvocations
                .computeIfAbsent(level, k -> new AtomicInteger())).incrementAndGet();

        return true;
    }

    @Override
    public boolean shouldCapture(MetricName name) {
        logger.info("{} -> shouldCapture(name: {})", Thread.currentThread().threadId(), name);
        ((AtomicInteger) shouldCaptureMetricInvocations
                .computeIfAbsent(name, k -> new AtomicInteger())).incrementAndGet();
        return true;
    }

    @Override
    public void incrementCounter(MetricName name) {
        logger.info("{} -> incrementCounter({})", Thread.currentThread().threadId(), name);

        incrementCounter(name, 1);
    }

    @Override
    public void incrementCounter(MetricName name, long amount) {
        logger.info("{} -> incrementCounter({}, {})", Thread.currentThread().threadId(), name, amount);

        if (!counters.containsKey(name)) {
            counters.put(name, amount);
            return;
        }
        counters.put(name, counters.get(name) + amount);
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
        logger.info("{} -> recordTimer({}, {})", Thread.currentThread().threadId(), name, durationNanos);

        timers.computeIfAbsent(name, _ -> new ArrayList<>()).add(durationNanos);
    }

    @Override
    public void registerGauge(MetricName name, Supplier<Number> valueSupplier) {
        logger.info("{} -> registerGauge({}, {})", Thread.currentThread().threadId(), name, valueSupplier);

        gauges.computeIfAbsent(name, _ -> new ArrayList<>()).add(valueSupplier.get());
    }

    @Override
    public void close() throws Exception {

    }

    public Map<MetricName, Long> getCounters() {
        return Collections.unmodifiableMap(counters);
    }

    public Map<MetricName, List<Long>> getTimers() {
        return Collections.unmodifiableMap(timers);
    }

    public Map<MetricName, List<Number>> getGauges() {
        return Collections.unmodifiableMap(gauges);
    }

    public DoubleSummaryStatistics getGaugeStats(MetricName name) {
        if (!gauges.containsKey(name)) {
            throw new IllegalStateException("Gauge not found: " + name);
        }

        return gauges.get(name).stream().mapToDouble(Number::doubleValue).summaryStatistics();
    }

    public LongSummaryStatistics getTimerStats(MetricName name) {
        if (!timers.containsKey(name)) {
            throw new IllegalStateException("Timer not found: " + name);
        }
        return timers.get(name).stream().mapToLong(Number::longValue).summaryStatistics();
    }

    public long getCounter(MetricName name) {
        if (!counters.containsKey(name)) {
            throw new IllegalStateException("Counter not found: " + name);
        }
        return counters.get(name);
    }

    public int getIsEnabledInvocations() {
        return isEnabledInvocations.get();
    }

    public int getShouldCaptureMetricInvocations(MetricName name) {
        return ((AtomicInteger) shouldCaptureMetricInvocations.get(name)).get();
    }

    public int getShouldCaptureLevelInvocations(MetricLevel level) {
        return ((AtomicInteger) shouldCaptureLevelInvocations.get(level)).get();
    }
}
