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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author nacho
 */
public class TelemetrySpy implements Telemetry {
    private final Logger logger = LoggerFactory.getLogger(TelemetrySpy.class);

    private final Map<MetricName, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<MetricName, List<Long>> timers = new ConcurrentHashMap<>();
    private final Map<MetricName, Supplier<Number>> gauges = new ConcurrentHashMap<>();
    private final Map<MetricName, List<Number>> gaugeValues = new ConcurrentHashMap<>();

    private final Map<MetricName, AtomicInteger> shouldCaptureMetricInvocations = new ConcurrentHashMap<>();

    private final Map<MetricLevel, AtomicInteger> shouldCaptureLevelInvocations = new ConcurrentHashMap<>();

    private final AtomicInteger isEnabledInvocations = new AtomicInteger(0);

    private boolean running = true;

    public TelemetrySpy() {
        for (MetricName metricName : MetricName.values()) {
            counters.put(metricName, new AtomicLong(0));
            timers.put(metricName, new CopyOnWriteArrayList<>());
            shouldCaptureMetricInvocations.put(metricName, new AtomicInteger(0));
        }
        for (MetricLevel metricLevel : MetricLevel.values()) {
            shouldCaptureLevelInvocations.put(metricLevel, new AtomicInteger(0));
        }
        Thread.ofPlatform().start(this::monitorGauges);
    }

    private void monitorGauges() {
        logger.info("Starting gauge monitor");
        while (running) {
            this.gauges.forEach((metricName, gauge) -> {
                var value = gauge.get();
                logger.info("Reading gauge '{}': {}", metricName, value);
                this.gaugeValues.get(metricName).add(value);
            });

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isEnabled() {
        logger.info("{} -> isEnabled", Thread.currentThread().threadId());
        isEnabledInvocations.incrementAndGet();
        return true;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        logger.info("{} -> shouldCapture(level: {})", Thread.currentThread().threadId(), level);
        shouldCaptureLevelInvocations.get(level).incrementAndGet();
        return true;
    }

    @Override
    public SamplingRate getSamplingRate() {
        return SamplingRate.EXTREME;
    }

    @Override
    public boolean shouldCapture(MetricName name) {
        logger.info("{} -> shouldCapture(name: {})", Thread.currentThread().threadId(), name);
        shouldCaptureMetricInvocations.get(name).incrementAndGet();

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
        counters.get(name).addAndGet(amount);
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
        logger.info("{} -> recordTimer({}, {})", Thread.currentThread().threadId(), name, durationNanos);
        timers.get(name).add(durationNanos);
    }

    @Override
    public void registerGauge(MetricName name, Number value) {
        Supplier<Number> valueSupplier = value::longValue;
        logger.info("{} -> registerGauge({}, {})", Thread.currentThread().threadId(), name, valueSupplier);
        gauges.put(name, valueSupplier);
        gaugeValues.put(name, new CopyOnWriteArrayList<>());
        gaugeValues.get(name).add(valueSupplier.get());
    }

    @Override
    public void close() throws Exception {
        this.running = false;
    }

    public Map<MetricName, Long> getCounters() {
        return Collections
                .unmodifiableMap(
                        counters
                                .entrySet()
                                .stream()
                                .collect(
                                        Collectors
                                                .toMap(
                                                        Map.Entry::getKey,
                                                        entry -> entry.getValue().get()
                                                )
                                )
                );

    }

    public Map<MetricName, List<Long>> getTimers() {
        return Collections.unmodifiableMap(timers);
    }

    public Map<MetricName, Supplier<Number>> getGauges() {
        return Collections.unmodifiableMap(gauges);
    }

    public Map<MetricName, List<Number>> getGaugeValues() {
        return Collections.unmodifiableMap(gaugeValues);
    }

    public DoubleSummaryStatistics getGaugeStats(MetricName name) {
        if (!gaugeValues.containsKey(name)) {
            throw new IllegalStateException("Gauge not found: " + name);
        }

        return gaugeValues.get(name).stream().mapToDouble(Number::doubleValue).summaryStatistics();
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
        return counters.get(name).get();
    }

    public int getIsEnabledInvocations() {
        return isEnabledInvocations.get();
    }

    public int getShouldCaptureMetricInvocations(MetricName name) {
        return shouldCaptureMetricInvocations.get(name).get();
    }

    public int getShouldCaptureLevelInvocations(MetricLevel level) {
        return shouldCaptureLevelInvocations.get(level).get();
    }
}
