/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.btrace.core.BTraceRuntimeBridge;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;
import org.openjdk.btrace.instr.BTraceTransformer;
import org.openjdk.btrace.instr.HandlerRepositoryImpl;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Measures the actual overhead of INVOKEDYNAMIC handler dispatch in real BTrace-instrumented code.
 *
 * <p>Setup uses the real {@link BTraceTransformer} pipeline to instrument {@link DispatchTarget}
 * with the compiled {@code DispatchScript.btclass} probe. The benchmark compares calling the
 * original (uninstrumented) methods against the instrumented versions that dispatch through
 * INVOKEDYNAMIC → ConstantCallSite → probe handler.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
public class DispatchBenchmark {

  private Workload original;
  private Workload instrumented;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    SharedSettings settings = SharedSettings.GLOBAL;
    settings.setTrusted(true);

    BTraceProbeFactory factory = new BTraceProbeFactory(settings);

    // Load compiled BTrace script (.btclass packaged by btracec task)
    byte[] scriptBytes;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("DispatchScript.btclass")) {
      if (is == null) {
        throw new IllegalStateException(
            "DispatchScript.btclass not found on classpath. Run btracec task first.");
      }
      scriptBytes = readAllBytes(is);
    }

    // Create probe from compiled script
    BTraceProbe probe = factory.createProbe(scriptBytes);

    // Get the probe class bytecode (with AnyType→Object transformation)
    byte[] probeBytes = probe.getFullBytecode();
    String probeClassName = probe.getClassName(false);

    // Read the original target class bytecode from classpath
    String targetResource =
        DispatchTarget.class.getName().replace('.', '/') + ".class";
    byte[] targetBytes;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(targetResource)) {
      if (is == null) {
        throw new IllegalStateException("DispatchTarget.class not found on classpath.");
      }
      targetBytes = readAllBytes(is);
    }

    // Install a no-op BTraceRuntimeAccess delegate so the probe class <clinit>
    // gets a non-null BTraceRuntimeBridge. Without this, forClass() returns null
    // and the generated error handler NPEs on runtime.handleException().
    BTraceRuntimeAccess.install(new NoOpDelegate());

    // Set up the classloader for probe and instrumented target.
    // Child-first for classes we define; parent-first for everything else
    // (BTrace runtime, JMH, java.*, etc.)
    InstrumentedClassLoader benchCL =
        new InstrumentedClassLoader(getClass().getClassLoader());
    benchCL.addClass(probeClassName, probeBytes);

    // Define the probe class so INVOKEDYNAMIC resolution can find it
    Class<?> probeClass = Class.forName(probeClassName, true, benchCL);

    // Set the definedClass field on the probe via reflection
    setDefinedClass(probe, probeClass);

    // Register probe in HandlerRepositoryImpl (drives INVOKEDYNAMIC resolution)
    HandlerRepositoryImpl.registerProbe(probe);

    // Create transformer and register probe for class matching
    BTraceTransformer transformer = new BTraceTransformer(new DebugSupport(settings));
    transformer.register(probe);

    // Transform the target class using the real instrumentation pipeline.
    // Pass benchCL (not system CL) to bypass the sensitive-class filter for org/openjdk/btrace/*.
    byte[] instrumentedBytes =
        transformer.transform(
            benchCL,
            DispatchTarget.class.getName().replace('.', '/'),
            null,
            null,
            targetBytes);

    if (instrumentedBytes == null) {
      throw new IllegalStateException(
          "BTraceTransformer did not instrument DispatchTarget. "
              + "Check that DispatchScript targets the correct class/methods.");
    }

    // Load the instrumented class in the child classloader
    benchCL.addClass(DispatchTarget.class.getName(), instrumentedBytes);
    Class<?> instrClass =
        Class.forName(DispatchTarget.class.getName(), true, benchCL);

    // Create instances
    original = new DispatchTarget();
    instrumented = (Workload) instrClass.getConstructor().newInstance();
  }

  // --- Benchmarks: entry handler (empty body, pure dispatch overhead) ---

  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void baseline_noArgs() {
    original.noArgs();
  }

  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public void instrumented_noArgs() {
    instrumented.noArgs();
  }

  // --- Benchmarks: return handler with @Duration ---

  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public long baseline_withReturn() {
    return original.withReturn();
  }

  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  public long instrumented_withReturn() {
    return instrumented.withReturn();
  }

  // --- Helpers ---

  private static void setDefinedClass(BTraceProbe probe, Class<?> clz) throws Exception {
    // Both BTraceProbeNode and BTraceProbePersisted have a 'definedClass' field
    Field field = null;
    Class<?> c = probe.getClass();
    while (c != null && field == null) {
      try {
        field = c.getDeclaredField("definedClass");
      } catch (NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    if (field == null) {
      throw new IllegalStateException("Cannot find 'definedClass' field on " + probe.getClass());
    }
    field.setAccessible(true);
    field.set(probe, clz);
  }

  private static byte[] readAllBytes(InputStream is) throws Exception {
    byte[] buf = new byte[8192];
    int pos = 0;
    int read;
    while ((read = is.read(buf, pos, buf.length - pos)) > 0) {
      pos += read;
      if (pos == buf.length) {
        byte[] newBuf = new byte[buf.length * 2];
        System.arraycopy(buf, 0, newBuf, 0, pos);
        buf = newBuf;
      }
    }
    byte[] result = new byte[pos];
    System.arraycopy(buf, 0, result, 0, pos);
    return result;
  }

  /**
   * ClassLoader that uses child-first delegation for explicitly added classes, parent-first for
   * everything else. This lets the instrumented DispatchTarget coexist with the original while
   * sharing the BTrace runtime, JMH, and java.* classes from the parent.
   */
  static final class InstrumentedClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes = new HashMap<>();

    InstrumentedClassLoader(ClassLoader parent) {
      super(parent);
    }

    void addClass(String name, byte[] bytes) {
      classes.put(name, bytes);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c != null) {
          return c;
        }
        byte[] bytes = classes.get(name);
        if (bytes != null) {
          c = defineClass(name, bytes, 0, bytes.length);
          if (resolve) {
            resolveClass(c);
          }
          return c;
        }
        return super.loadClass(name, resolve);
      }
    }
  }

  /** No-op delegate that satisfies the probe class {@code <clinit>} contract. */
  static final class NoOpDelegate implements BTraceRuntimeAccess.Delegate {
    private static final BTraceRuntimeBridge NOOP_BRIDGE = new NoOpBridge();

    @Override
    public boolean enter(BTraceRuntimeBridge currentRt) {
      return true;
    }

    @Override
    public void leave() {}

    @Override
    public BTraceRuntimeBridge forClass(
        Class cl,
        TimerHandler[] tHandlers,
        EventHandler[] evHandlers,
        ErrorHandler[] errHandlers,
        ExitHandler[] eHandlers,
        LowMemoryHandler[] lmHandlers) {
      return NOOP_BRIDGE;
    }

    @Override
    public ThreadLocal newThreadLocal(Object initValue) {
      return ThreadLocal.withInitial(() -> initValue);
    }

    @Override
    public String getClientName(String forClassName) {
      return forClassName;
    }

    @Override
    public ExtensionContext currentContext() {
      return null;
    }
  }

  /** No-op bridge returned by {@link NoOpDelegate#forClass}. */
  static final class NoOpBridge implements BTraceRuntimeBridge {
    @Override public void start() {}
    @Override public void leave() {}
    @Override public void handleException(Throwable th) {}
    @Override public boolean isDisabled() { return false; }
    @Override public void newPerfCounter(Object value, String name, String desc) {}
    @Override public int getPerfInt(String name) { return 0; }
    @Override public void putPerfInt(int value, String name) {}
    @Override public float getPerfFloat(String name) { return 0f; }
    @Override public void putPerfFloat(float value, String name) {}
    @Override public long getPerfLong(String name) { return 0L; }
    @Override public void putPerfLong(long value, String name) {}
    @Override public String getPerfString(String name) { return ""; }
    @Override public void putPerfString(String value, String name) {}
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(".*" + DispatchBenchmark.class.getSimpleName() + ".*")
            .build();

    new Runner(opt).run();
  }
}
