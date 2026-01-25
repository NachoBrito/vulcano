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
 * Defines the sampling rate for telemetry metrics.
 * Sampling helps reduce the overhead of metrics collection in hot paths by only
 * capturing a fraction of the events.
 *
 * @author nacho
 */
public enum SamplingRate {
    /**
     * No metrics are captured.
     */
    OFF(0),
    /**
     * Very low sampling rate (~1 in 1024).
     */
    LOW(1024),
    /**
     * Medium sampling rate (~1 in 256).
     */
    MEDIUM(256),
    /**
     * High sampling rate (~1 in 2).
     */
    HIGH(2),
    /**
     * All metrics are captured (1 in 1).
     */
    EXTREME(1);

    private final int mask;

    SamplingRate(int value) {
        this.mask = value - 1;
    }

    boolean shouldSample() {
        if (this.equals(OFF)) {
            return false;
        }
        if (this.equals(EXTREME)) {
            return true;
        }
        return 0 == (System.nanoTime() & this.mask);
    }

}
