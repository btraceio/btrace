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

import io.btrace.core.SharedSettings;
import io.btrace.instr.BTraceProbe;
import io.btrace.instr.BTraceProbeFactory;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class ProbeLoadingBenchmark {
  private InputStream classStream;
  private BTraceProbeFactory bpf;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    bpf = new BTraceProbeFactory(SharedSettings.GLOBAL);
  }

  @Setup(Level.Invocation)
  public void setupRun() throws Exception {
    classStream = ProbeLoadingBenchmark.class.getResourceAsStream("/TraceScript.btclass");
  }

  @TearDown(Level.Invocation)
  public void tearDownRun() throws Exception {
    classStream.close();
  }

  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void testBTraceProbeNew(Blackhole bh) throws Exception {
    BTraceProbe bp = bpf.createProbe(classStream);
    if (bp == null) {
      throw new NullPointerException();
    }
    bh.consume(bp);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .addProfiler("stack")
            .include(".*" + ProbeLoadingBenchmark.class.getSimpleName() + ".*test.*")
            .build();

    new Runner(opt).run();
  }
}
