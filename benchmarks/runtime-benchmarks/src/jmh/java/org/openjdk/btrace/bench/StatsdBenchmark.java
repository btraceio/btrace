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
package org.openjdk.btrace.bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.btrace.statsd.Statsd;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Basic benchmark for the performance of {@linkplain Statsd}
 *
 * @author Jaroslav Bachorik
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class StatsdBenchmark {
  private Statsd c;

  @Setup
  public void setup() {
    // Inline no-op impl — the benchmark measures dispatch through the extension API,
    // not the network-layer Statsd implementation.
    c =
        new Statsd() {
          @Override
          public void increment(String name) {}

          @Override
          public void increment(String name, String tags) {}
        };
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(1)
  public void testIncrement_1() {
    c.increment("g1");
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("stack")
            .include(".*" + StatsdBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
