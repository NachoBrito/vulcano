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
 * @author nacho
 */
public final class NoOpTelemetry implements Telemetry {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean shouldCapture(MetricLevel level) {
        return false;
    }

    @Override
    public boolean shouldCapture(MetricName name) {
        return false;
    }

    @Override
    public void incrementCounter(MetricName name) {
    }

    @Override
    public void incrementCounter(MetricName name, long amount) {
    }

    @Override
    public void recordTimer(MetricName name, long durationNanos) {
    }

    @Override
    public void registerGauge(MetricName name, Supplier<Number> valueSupplier) {
    }

    @Override
    public void close() throws Exception {

    }
}
