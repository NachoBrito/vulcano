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

package es.nachobrito.vulcanodb.micronaut;

import es.nachobrito.vulcanodb.core.telemetry.MetricLevel;
import es.nachobrito.vulcanodb.core.telemetry.MetricName;
import es.nachobrito.vulcanodb.core.telemetry.SamplingRate;
import es.nachobrito.vulcanodb.core.telemetry.Telemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author nacho
 */
@Singleton
@Requires(property = "vulcanodb.enabled", value = "true")
public class MicronautTelemetry implements Telemetry {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final boolean telemetryEnabled;
    private final MetricLevel metricLevel;
    private final MeterRegistry meterRegistry;
    private final SamplingRate samplingRate;

    private final Map<MetricName, Counter> counters = new EnumMap<>(MetricName.class);
    private final Map<MetricName, Timer> timers = new EnumMap<>(MetricName.class);
    private final Map<MetricName, Number> metricValues = new EnumMap<>(MetricName.class);

    public MicronautTelemetry(
            @Property(name = "vulcanodb.telemetry.enabled", defaultValue = "false")
            String telemetryEnabled,
            @Property(name = "vulcanodb.telemetry.level", defaultValue = "BASIC")
            String metricLevel,
            @Property(name = "vulcano.telemetry.sampling", defaultValue = "MEDIUM")
            String samplingRate,
            MeterRegistry meterRegistry
    ) {
        this.telemetryEnabled = Boolean.parseBoolean(telemetryEnabled);
        this.metricLevel = MetricLevel.valueOf(metricLevel);
        this.samplingRate = SamplingRate.valueOf(samplingRate);
        this.meterRegistry = meterRegistry;
        if (this.telemetryEnabled) {
            log.info("VulcanoDb telemetry (level: {}) will be shown in Micronaut metrics", metricLevel);
            preRegisterMeters();
        } else {
            log.info("VulcanoDb telemetry disabled in Micronaut metrics");
        }

    }

    private void preRegisterMeters() {
        for (MetricName name : MetricName.values()) {
            // Check if name has a defined type or is basic enough to pre-bind
            if (name.name().contains("COUNT")) {
                log.info("Registering counter: {}", name);
                counters.put(name, meterRegistry.counter(name.getKey()));
            } else if (name.name().contains("LATENCY") || name.name().contains("TIMER")) {
                log.info("Registering timer: {}", name);
                timers.put(name, Timer.builder(name.getKey())
                        .publishPercentiles(0.5, 0.9, 0.99) // Client-side percentiles
                        .publishPercentileHistogram(true) // Histogram buckets for server-side percentiles
                        .register(meterRegistry));
            }
        }
    }


    @Override
    public boolean isEnabled() {
        return this.telemetryEnabled;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        var should = telemetryEnabled && level.ordinal() <= metricLevel.ordinal();
        if (log.isDebugEnabled()) {
            log.debug("Should Capture Telemetry Level: {}? {}", level, should);
        }
        return should;
    }

    @Override
    public void incrementCounter(MetricName name) {
        if (log.isDebugEnabled()) {
            log.debug("Increment Counter for metric {}", name);
        }
        counters.get(name).increment();
    }

    @Override
    public void incrementCounter(MetricName name, long amount) {
        if (log.isDebugEnabled()) {
            log.debug("Increment Counter for metric {} by {}", name, amount);
        }
        counters.get(name).increment(amount);
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
        if (log.isDebugEnabled()) {
            log.debug("Record Timer for metric {}: {} nanos", name, durationNanos);
        }
        timers.get(name)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void registerGauge(MetricName name, Number value) {
        if (log.isDebugEnabled()) {
            log.debug("Register Gauge for metric {}", name);
        }
        meterRegistry.gauge(name.getKey(), value);
        metricValues.put(name, value);
    }

    @Override
    public SamplingRate getSamplingRate() {
        return samplingRate;
    }

    @Override
    public void close() throws Exception {
        //nothing to do
    }
}
