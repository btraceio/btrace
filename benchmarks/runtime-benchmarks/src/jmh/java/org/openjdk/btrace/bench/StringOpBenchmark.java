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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class StringOpBenchmark {
  private static final String STRING_PART = "h";

  StringBuilder sb;
  String st;
  String res;

  @Setup
  public void setup() {
    st = "";
  }

  @Setup(Level.Invocation)
  public void setupEach() {
    sb = new StringBuilder();
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testStringBuilder() {
    sb.append(STRING_PART).append(STRING_PART);
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testStringPlus() {
    res = st + STRING_PART + STRING_PART;
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testStrCat() {
    res = st.concat(STRING_PART).concat(STRING_PART);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(".*" + StringOpBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
