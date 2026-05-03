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
package io.btrace.bench;

import io.btrace.runtime.profiling.MethodInvocationProfiler;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.runner.options.VerboseMode;

/**
 * Basic benchmark for the performance of {@linkplain MethodInvocationProfiler}
 *
 * @author Jaroslav Bachorik
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class ProfilerBenchmark {
  private MethodInvocationProfiler mip1;
  private MethodInvocationProfiler mip2;

  @Setup
  public void setup() {
    mip1 = new MethodInvocationProfiler(1);
    mip2 = new MethodInvocationProfiler(500);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(1)
  public void testOneMethodSingleThread() {
    mip1.recordEntry("a");
    mip1.recordExit("a", 1);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(1)
  public void testTwoMethods01Thread() {
    mip2.recordEntry("a");
    mip2.recordEntry("b");
    mip2.recordExit("b", 10);
    mip2.recordExit("a", 1);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(2)
  public void testTwoMethods02Threads() {
    mip2.recordEntry("a");
    mip2.recordEntry("b");
    mip2.recordExit("b", 10);
    mip2.recordExit("a", 1);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(4)
  public void testTwoMethods04Threads() {
    mip2.recordEntry("a");
    mip2.recordEntry("b");
    mip2.recordExit("b", 10);
    mip2.recordExit("a", 1);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(8)
  public void testTwoMethods08Threads() {
    mip2.recordEntry("a");
    mip2.recordEntry("b");
    mip2.recordExit("b", 10);
    mip2.recordExit("a", 1);
  }

  @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  @Threads(16)
  public void testTwoMethods16Threads() {
    mip2.recordEntry("a");
    mip2.recordEntry("b");
    mip2.recordExit("b", 10);
    mip2.recordExit("a", 1);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("stack")
            .verbosity(VerboseMode.NORMAL)
            .include(".*" + ProfilerBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
