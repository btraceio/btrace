/*
 * Copyright (c) 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */
package net.java.btrace;

import com.sun.btrace.runtime.ClassFilter;
import com.sun.btrace.runtime.OnMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
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
        Options opt = new OptionsBuilder()
                    .addProfiler("stack")
                    .include(".*" + ClassFilterBenchmark.class.getSimpleName() + ".*test.*")
                    .build();

            new Runner(opt).run();
    }
}
