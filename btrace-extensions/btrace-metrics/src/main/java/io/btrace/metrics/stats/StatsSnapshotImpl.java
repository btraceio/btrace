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

import org.jetbrains.annotations.NotNull;

/** Immutable snapshot of statistics. */
public final class StatsSnapshotImpl implements StatsSnapshot {

  private final String name;
  private final long count;
  private final long sum;
  private final long min;
  private final long max;
  private final double mean;
  private final double stddev;

  public StatsSnapshotImpl(
      String name, long count, long sum, long min, long max, double mean, double stddev) {
    this.name = name;
    this.count = count;
    this.sum = sum;
    this.min = min;
    this.max = max;
    this.mean = mean;
    this.stddev = stddev;
  }

  @NotNull public String getName() {
    return name;
  }

  public long count() {
    return count;
  }

  public long sum() {
    return sum;
  }

  public long min() {
    return min;
  }

  public long max() {
    return max;
  }

  public double mean() {
    return mean;
  }

  public double stddev() {
    return stddev;
  }
}
