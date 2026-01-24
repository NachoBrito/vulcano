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
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
        log.info("VulcanoDb telemetry (level: {}) will be shown in Micronaut metrics", metricLevel);
    }

    @Override
    public boolean isEnabled() {
        return this.telemetryEnabled;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        return telemetryEnabled && level.ordinal() <= metricLevel.ordinal();
    }

    @Override
    public void incrementCounter(MetricName name) {
        meterRegistry
                .counter(name.getKey())
                .increment();
    }

    @Override
    public void incrementCounter(MetricName name, long amount) {
        meterRegistry
                .counter(name.getKey())
                .increment(amount);
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
        meterRegistry
                .timer(name.getKey())
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void registerGauge(MetricName name, Supplier<Number> valueSupplier) {
        meterRegistry
                .gauge(name.getKey(), valueSupplier, (supplier) -> {
                    Number val = supplier.get();
                    return val == null ? Double.NaN : val.doubleValue();
                });
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
