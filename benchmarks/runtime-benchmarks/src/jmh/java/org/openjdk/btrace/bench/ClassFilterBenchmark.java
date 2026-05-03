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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import io.btrace.instr.ClassFilter;
import io.btrace.instr.OnMethod;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class ClassFilterBenchmark {
  private static final String CLASS_A_PKG = "io.btrace.benchmark";
  private static final String CLASS_A_NAME = "ClassA";
  private static final String CLASS_A = CLASS_A_PKG + "." + CLASS_A_NAME;

  private ClassFilter cfSimple;
  private ClassFilter cfRegexName;
  private ClassFilter cfSubtype;

  @Setup
  public void setup() {
    OnMethod simpleClassFilter = new OnMethod();
    simpleClassFilter.setClazz(CLASS_A);

    OnMethod regexNameFilter = new OnMethod();
    regexNameFilter.setClazz("/.*\\." + CLASS_A_NAME + "/");

    OnMethod subtypeFilter = new OnMethod();
    subtypeFilter.setClazz("+java.util.List");

    cfSimple = new ClassFilter(Collections.singleton(simpleClassFilter));
    cfRegexName = new ClassFilter(Collections.singleton(regexNameFilter));
    cfSubtype = new ClassFilter(Collections.singleton(subtypeFilter));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testSimpleClassNameMatch(Blackhole bh) {
    bh.consume(cfSimple.isNameMatching(CLASS_A));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testRegexNameMatch(Blackhole bh) {
    bh.consume(cfRegexName.isNameMatching(CLASS_A));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testSubtypeMatch(Blackhole bh) {
    bh.consume(cfSubtype.isCandidate(ArrayList.class));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testSubtypeNoMatch(Blackhole bh) {
    bh.consume(cfSubtype.isCandidate(String.class));
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("stack")
            .include(".*" + ClassFilterBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
