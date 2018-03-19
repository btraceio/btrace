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
package com.sun.btrace.runtime;

import com.sun.btrace.ArgsMap;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.SharedSettings;
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
public class OnMethodTemplateBenchmark {
    private ArgsMap argsMap;

    @Setup
    public void setup() {
        argsMap = new ArgsMap(new String[]{"arg1=val1"}, new DebugSupport(SharedSettings.GLOBAL));
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
        Options opt = new OptionsBuilder()
                    .addProfiler("stack")
                    .include(".*" + OnMethodTemplateBenchmark.class.getSimpleName() + ".*test.*")
                    .build();

            new Runner(opt).run();
    }
}
