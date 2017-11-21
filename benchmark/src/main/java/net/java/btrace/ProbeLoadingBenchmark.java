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

import com.sun.btrace.SharedSettings;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.runtime.BTraceProbe;
import com.sun.btrace.runtime.BTraceProbeFactory;
import com.sun.btrace.runtime.BTraceProbeNode;
import com.sun.btrace.runtime.BTraceProbePersisted;
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
        classStream = ProbeLoadingBenchmark.class.getResourceAsStream("/scripts/TraceScript.class");
    }

    @TearDown(Level.Invocation)
    public void tearDownRun() throws Exception {
        classStream.close();
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testBTraceProbeNew(Blackhole bh) throws Exception {
        BTraceProbe bp = bpf.createProbe(classStream);
        if (bp == null) {
            throw new NullPointerException();
        }
        bh.consume(bp);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                    .addProfiler("stack")
                    .include(".*" + ProbeLoadingBenchmark.class.getSimpleName() + ".*test.*")
                    .build();

            new Runner(opt).run();
    }
}
