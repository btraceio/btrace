/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.metrics.stats;

import io.btrace.metrics.Metric;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Statistics metric using lock-free atomics.
 *
 * <p>Provides: count, sum, min, max, average, stddev
 *
 * <p>Uses LongAdder for count/sum (better than AtomicLong under contention). Uses AtomicLong for
 * min/max with CAS loop.
 */
public final class StatsMetricImpl implements StatsMetric, Metric {

  private final String name;
  private final LongAdder count = new LongAdder();
  private final LongAdder sum = new LongAdder();
  private final LongAdder sumOfSquares = new LongAdder();
  private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

  public StatsMetricImpl(String name) {
    this.name = name;
  }

  /**
   * Record value - lock-free.
   *
   * @param value value to record
   */
  public void record(long value) {
    count.increment();
    sum.add(value);
    sumOfSquares.add(value * value);

    // Update min (CAS loop)
    long currentMin;
    do {
      currentMin = min.get();
      if (value >= currentMin) {
        break;
      }
    } while (!min.compareAndSet(currentMin, value));

    // Update max (CAS loop)
    long currentMax;
    do {
      currentMax = max.get();
      if (value <= currentMax) {
        break;
      }
    } while (!max.compareAndSet(currentMax, value));
  }

  /**
   * Get immutable snapshot.
   *
   * @return snapshot
   */
  @Nullable public StatsSnapshot snapshot() {
    long cnt = count.sum();
    long sm = sum.sum();
    long sumSq = sumOfSquares.sum();
    long mn = min.get();
    long mx = max.get();

    if (cnt == 0) {
      return new StatsSnapshotImpl(name, 0, 0, 0, 0, 0, 0);
    }

    double avg = (double) sm / cnt;

    // Variance = E[X^2] - E[X]^2
    double variance = ((double) sumSq / cnt) - (avg * avg);
    double stddev = Math.sqrt(Math.max(0, variance));

    return new StatsSnapshotImpl(name, cnt, sm, mn, mx, avg, stddev);
  }

  @Override
  public void reset() {
    count.reset();
    sum.reset();
    sumOfSquares.reset();
    min.set(Long.MAX_VALUE);
    max.set(Long.MIN_VALUE);
  }

  @Override
  @NotNull public String getName() {
    return name;
  }
}
