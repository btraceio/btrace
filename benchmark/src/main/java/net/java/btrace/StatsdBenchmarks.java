/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package net.java.btrace;

import com.sun.btrace.profiling.MethodInvocationProfiler;
import com.sun.btrace.services.impl.Statsd;
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
import org.openjdk.jmh.profile.ProfilerFactory;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Basic benchmark for the performance of {@linkplain MethodInvocationProfiler}
 * @author Jaroslav Bachorik
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class StatsdBenchmarks {
    private Statsd c;

    @Setup
    public void setup() {
        c = Statsd.getInstance();
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    @Threads(1)
    public void testGauge_1() {
        c.gauge("g1", 10);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .addProfiler(ProfilerFactory.getProfilerByName("stack"))
                .include(".*" + StatsdBenchmarks.class.getSimpleName() + ".*test.*")
                .build();

        new Runner(opt).run();
    }
}
