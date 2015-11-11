/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.CommandListener;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.OkayCommand;
import com.sun.btrace.instr.MethodTracker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import scripts.TraceScript;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class BTraceBench {

    private static class BTraceConfig {

        private final String agentJar;
        private final String scriptPath;
        private final Path tmpRoot;

        private static final FileVisitor<Path> DEL_TREE = new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };

        public BTraceConfig(Path tmpRoot, String agentJar, String scriptPath) {
            this.agentJar = agentJar;
            this.scriptPath = scriptPath;
            this.tmpRoot = tmpRoot;
        }

        public void cleanup() throws IOException {
            Files.walkFileTree(tmpRoot, DEL_TREE);
        }
    }

    long counter;
    long sampleCounter;
    long durCounter;

    BTraceRuntime br;
    LinkedBlockingQueue<String> l = new LinkedBlockingQueue<>();
    PrintWriter pw;
    CommandListener cl;

    @Setup
    public void setup() {
        MethodTracker.registerCounter(1, 10);
        MethodTracker.registerCounter(2, 50);
        MethodTracker.registerCounter(3, 100);

        Random r = new Random(System.currentTimeMillis());
        sampleCounter = 0;
        durCounter = 0;
        counter = r.nextInt();
        try {
            FileOutputStream fos = new FileOutputStream("/tmp/test.dump");
            pw = new PrintWriter(fos);
            cl = (c) -> {
                if (c instanceof DataCommand) {
                    ((DataCommand) c).print(pw);
                }
            };
        } catch (Exception e) {
            cl = (c) -> {
            };
        }
        br = new BTraceRuntime("BenchmarkClass", new String[0], cl, null, null);
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethod() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodLevelNoMatch() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodSampled() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodPrintln1() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodPrintln1Sampled() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodPrintln2() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodPrintln3() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrumentedMethodPrintln24() {
        counter++;
    }

    @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testMethod() {
        counter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrDuration() {
        durCounter++;
    }

    public boolean x = true;
    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrDurationSampled() {
        sampleCounter++;
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testInstrDurationSampledAdaptive() {
        sampleCounter++;
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testSendCommand() {
        br.send(new OkayCommand());
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
    @Threads(2)
    @Benchmark
    public void testSendCommandMulti2() {
        br.send(new OkayCommand());
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
    @Threads(4)
    @Benchmark
    public void testSendCommandMulti4() {
        br.send(new OkayCommand());
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
    @Threads(8)
    @Benchmark
    public void testSendCommandMulti8() {
        br.send(new OkayCommand());
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
    @Threads(16)
    @Benchmark
    public void testSendCommandMulti16() {
        br.send(new OkayCommand());
    }

    long sampleHit10Checks = 0;
    long sampleHit10Sampled = 0;

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    @Threads(2)
    public void testSampleHit10() {
        sampleHit10Checks++;
        if (MethodTracker.hit(1)) {
            sampleHit10Sampled++;
        }
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    @Threads(2)
    public void testSampleHit50() {
        sampleHit10Checks++;
        if (MethodTracker.hit(2)) {
            sampleHit10Sampled++;
        }
    }

    @Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    @Threads(2)
    public void testSampleHit100() {
        sampleHit10Checks++;
        if (MethodTracker.hit(3)) {
            sampleHit10Sampled++;
        }
    }

    @org.openjdk.jmh.annotations.TearDown
    public void teardown() {
        System.err.println();
        if (sampleHit10Checks > 0) {
            System.err.println("=== testSampleHit10");
            System.err.println("#samples ~ " + sampleHit10Sampled);
            if (sampleHit10Sampled > 0) {
                System.err.println("#sampling rate ~ " + (sampleHit10Checks / sampleHit10Sampled));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        BTraceConfig bc = getConfig();
        try {
            Options opt = new OptionsBuilder()
                    .addProfiler("stack")
                    .jvmArgsPrepend("-javaagent:" + bc.agentJar + "=noServer=true,"
                            + "script=" + bc.scriptPath)
                    .include(".*" + BTraceBench.class.getSimpleName() + ".*test.*")
                    .build();

            new Runner(opt).run();
        } finally {
            bc.cleanup();
        }
    }

    private static BTraceConfig getConfig() throws IOException {
        FileSystem fs = FileSystems.getDefault();

        Path agentPath = null;
        Path bootPath = null;
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL[] urls = ((URLClassLoader)cl).getURLs();
        for (URL url: urls) {
            final String path = url.getPath();
            if (path.contains("btrace-agent")) {
                agentPath = fs.getPath(path);
            } else if (path.contains("btrace-boot")) {
                bootPath = fs.getPath(path);
            }
        }
        if (agentPath == null) { throw new IllegalArgumentException("btrace-agent.jar not found"); }
        if (bootPath == null) { throw new IllegalArgumentException("btrace-boot.jar not found"); }

        Path tmpDir = Files.createTempDirectory("btrace-bench-");

        Path targetPath = Files.copy(agentPath, tmpDir.resolve("btrace-agent.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(bootPath, tmpDir.resolve("btrace-boot.jar"), StandardCopyOption.REPLACE_EXISTING);

        URL traceLoc = BTraceBench.class.getResource("/" + TraceScript.class.getName().replace('.', '/') + ".class");
        String trace = traceLoc.getPath();

        return new BTraceConfig(tmpDir, targetPath.toString(), trace);
    }
}
