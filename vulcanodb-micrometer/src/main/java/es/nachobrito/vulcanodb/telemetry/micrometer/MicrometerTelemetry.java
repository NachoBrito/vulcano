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

package es.nachobrito.vulcanodb.telemetry.micrometer;

import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author nacho
 */
public abstract class MicrometerTelemetry implements Telemetry {
    private final MeterRegistry registry;
    private final boolean enabled;
    private final MetricLevel configLevel;

    // Fast lookup maps to avoid re-binding meters in the hot path
    private final Map<MetricName, Counter> counters = new EnumMap<>(MetricName.class);
    private final Map<MetricName, Timer> timers = new EnumMap<>(MetricName.class);

    public MicrometerTelemetry(MeterRegistry registry, boolean enabled, MetricLevel configLevel) {
        this.registry = registry;
        this.enabled = enabled;
        this.configLevel = configLevel;

        if (enabled) {
            preRegisterMeters();
        }
    }

    private void preRegisterMeters() {
        for (MetricName name : MetricName.values()) {
            // Check if name has a defined type or is basic enough to pre-bind
            if (name.name().contains("COUNT")) {
                counters.put(name, registry.counter(name.getKey()));
            } else if (name.name().contains("LATENCY") || name.name().contains("TIMER")) {
                timers.put(name, Timer.builder(name.getKey())
                        .publishPercentiles(0.5, 0.9, 0.99) // Built-in histogram support
                        .register(registry));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        return enabled && level.ordinal() <= configLevel.ordinal();
    }

    @Override
    public void incrementCounter(MetricName name) {
        Counter counter = counters.get(name);
        if (counter != null) {
            counter.increment();
        }
    }

    @Override
    public void incrementCounter(MetricName name, long amount) {
        Counter counter = counters.get(name);
        if (counter != null) {
            counter.increment(amount);
        }
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
        Timer timer = timers.get(name);
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void registerGauge(MetricName name, Supplier<Number> valueSupplier) {
        // Micrometer handles the polling of the supplier automatically
        Gauge.builder(name.getKey(), valueSupplier)
                .register(registry);
    }
}
