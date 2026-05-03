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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmark measuring INVOKEDYNAMIC dispatch overhead as simulated by {@link ConstantCallSite}
 * — the mechanism used by {@code IndyDispatcher}.
 *
 * <p>Compares a plain static method call ({@link #baseline}) against dispatch through a {@link
 * ConstantCallSite} ({@link #instrumented}).
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class DispatchBenchmark {

  private MethodHandle constantTarget;
  private MethodHandle mutableTarget;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    // Build a ConstantCallSite targeting the static handler method, simulating what
    // IndyDispatcher.bootstrap() produces.
    MethodHandle mh =
        MethodHandles.lookup()
            .findStatic(
                DispatchBenchmark.class,
                "probeHandler",
                MethodType.methodType(void.class, int.class));
    CallSite cs = new ConstantCallSite(mh);
    constantTarget = cs.dynamicInvoker();
    MutableCallSite mcs = new MutableCallSite(mh.type());
    mcs.setTarget(mh);
    mutableTarget = mcs.dynamicInvoker();
  }

  /** Direct static call — baseline with zero dispatch overhead. */
  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void baseline(Blackhole bh) {
    probeHandler(42);
  }

  /** Dispatch through a ConstantCallSite — simulates IndyDispatcher-resolved call site. */
  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void instrumented(Blackhole bh) throws Throwable {
    constantTarget.invokeExact(42);
  }

  /**
   * Dispatch through a MutableCallSite whose target is stable (set once, never re-linked).
   * Simulates the IndyDispatcher-post-detach-safety variant. HotSpot should treat the target
   * as @Stable and inline through it comparably to ConstantCallSite.
   */
  @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void instrumentedMutable(Blackhole bh) throws Throwable {
    mutableTarget.invokeExact(42);
  }

  /** Simulated probe handler method. */
  public static void probeHandler(int value) {
    // intentionally empty — we measure dispatch cost, not handler body cost
  }
}
