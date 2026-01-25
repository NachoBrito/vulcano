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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static es.nachobrito.vulcanodb.core.telemetry.SamplingRate.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nacho
 */
class SamplingRateTest {

    private final List<Long> times = new ArrayList<>();

    @Test
    @Disabled
    void shouldSample() {
        final double epsilon = .01;
        assertSamplingRate(0, OFF, epsilon);
        assertSamplingRate(1.0 / 1024, LOW, epsilon);
        assertSamplingRate(1.0 / 256, MEDIUM, epsilon);
        assertSamplingRate(0.5, HIGH, epsilon);
        assertSamplingRate(1, EXTREME, epsilon);

        var bitshiftStats = times.stream().mapToLong(Long::longValue).summaryStatistics();

        IO.println("bitshiftStats = " + bitshiftStats);
    }

    private void assertSamplingRate(double rate, SamplingRate samplingRate, double epsilon) {
        var stats = IntStream
                .range(0, 1000)
                .mapToObj(_ -> {
                    try {
                        Thread.sleep(1);
                    } catch (Exception ignored) {
                    }
                    var t0 = System.nanoTime();
                    var result = samplingRate.shouldSample();
                    times.add(System.nanoTime() - t0);
                    return result ? 1 : 0;
                })
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        var diff = Math.abs(rate - stats.getAverage());
        assertTrue(epsilon >= diff);
    }
}