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
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class OnMethodTemplateBenchmark {
  private ArgsMap argsMap;

  @Setup
  public void setup() {
    argsMap = new ArgsMap(new String[] {"arg1=val1"});
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testEmptyTemplate(Blackhole bh) {
    bh.consume(argsMap.template(""));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testMatchTemplate(Blackhole bh) {
    bh.consume(argsMap.template("this-is-${arg1}"));
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1200, timeUnit = TimeUnit.MILLISECONDS)
  @Benchmark
  public void testNoMatchTemplate(Blackhole bh) {
    bh.consume(argsMap.template("this-is-${arg2}"));
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("stack")
            .include(".*" + OnMethodTemplateBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
