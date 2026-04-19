/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.bench;

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
 * JMH benchmark measuring INVOKEDYNAMIC dispatch overhead as simulated by
 * {@link ConstantCallSite} — the mechanism used by {@code IndyDispatcher}.
 *
 * <p>Compares a plain static method call ({@link #baseline}) against dispatch
 * through a {@link ConstantCallSite} ({@link #instrumented}).
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
