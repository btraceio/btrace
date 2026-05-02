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


package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jdk.internal.perf.Perf;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.jfr.JfrEvent;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;

/**
 * Helper class used by BTrace built-in functions and also acts runtime "manager" for a specific
 * BTrace client and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
public final class BTraceRuntimeImpl_11 extends BTraceRuntimeImplBase {
  public static final class Factory extends BTraceRuntimeImplFactory<BTraceRuntimeImpl_11> {

    public Factory() {
      super(new BTraceRuntimeImpl_11());
    }

    @Override
    public BTraceRuntimeImpl_11 getRuntime(
        String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
      return new BTraceRuntimeImpl_11(className, args, cmdListener, inst);
    }

    @Override
    public boolean isEnabled() {
      Runtime.Version version = Runtime.version();
      return version.version().get(0) >= 11;
    }
  }
  // perf counter variability - we always variable variability
  private static final int V_Variable = 3;
  // perf counter units
  private static final int V_None = 1;
  private static final int V_String = 5;
  private static final int PERF_STRING_LIMIT = 256;

  private static Perf perf;

  private final Set<JfrEventFactoryImpl> eventFactories =
      new java.util.concurrent.CopyOnWriteArraySet<>();

  private final Method findBootstrapOrNullMtd;

  private static volatile Boolean jfrAvailable = null;
  private static final Object jfrLock = new Object();

  private static boolean isJfrAvailable() {
    if (jfrAvailable == null) {
      synchronized (jfrLock) {
        if (jfrAvailable == null) {
          try {
            Class.forName("jdk.jfr.EventType");
            jfrAvailable = Boolean.TRUE;
          } catch (ClassNotFoundException | LinkageError e) {
            jfrAvailable = Boolean.FALSE;
          }
        }
      }
    }
    return jfrAvailable;
  }

  public BTraceRuntimeImpl_11() {
    fixExports(BTraceRuntime.instrumentation);

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  public BTraceRuntimeImpl_11(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    super(className, args, cmdListener, fixExports(inst));

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  private static Instrumentation fixExports(Instrumentation instr) {
    Set<Module> myModules = Collections.singleton(BTraceRuntimeImpl_11.class.getModule());
    if (instr != null) {
      Module javaBaseMod = int.class.getModule();
      Module jfrMod = null;
      if (isJfrAvailable()) {
        try {
          Class<?> eventTypeClass = Class.forName("jdk.jfr.EventType");
          jfrMod = eventTypeClass.getModule();
        } catch (ClassNotFoundException | LinkageError e) {
          // JFR not available, skip JFR module setup
        }
      }

      // Build export/open maps only for packages actually present in the module to avoid
      // IllegalArgumentException on newer JDKs (e.g., 25+) where some packages are removed.
      Set<String> basePkgs = javaBaseMod.getPackages();
      java.util.Map<String, Set<Module>> extraExports = new java.util.HashMap<>();
      java.util.Map<String, Set<Module>> extraOpens = new java.util.HashMap<>();

      if (basePkgs.contains("java.lang")) {
        extraExports.put("java.lang", myModules);
        extraOpens.put("java.lang", myModules);
      }
      if (basePkgs.contains("jdk.internal.reflect")) {
        extraExports.put("jdk.internal.reflect", myModules);
      }
      if (basePkgs.contains("jdk.internal.perf")) {
        extraExports.put("jdk.internal.perf", myModules);
      }
      if (basePkgs.contains("sun.security.action")) {
        extraExports.put("sun.security.action", myModules);
      }

      if (!extraExports.isEmpty() || !extraOpens.isEmpty()) {
        instr.redefineModule(
            javaBaseMod,
            Collections.emptySet(),
            extraExports,
            extraOpens,
            Collections.emptySet(),
            Collections.emptyMap());
      }

      // jdk.jfr internal access - only if JFR module is available
      if (jfrMod != null) {
        java.util.Map<String, Set<Module>> jfrExports = new java.util.HashMap<>();
        java.util.Map<String, Set<Module>> jfrOpens = new java.util.HashMap<>();
        Set<String> jfrPkgs = jfrMod.getPackages();
        if (jfrPkgs.contains("jdk.jfr.internal")) {
          jfrExports.put("jdk.jfr.internal", myModules);
        }
        if (jfrPkgs.contains("jdk.jfr")) {
          jfrOpens.put("jdk.jfr", myModules);
        }
        if (!jfrExports.isEmpty() || !jfrOpens.isEmpty()) {
          instr.redefineModule(
              jfrMod,
              Collections.emptySet(),
              jfrExports,
              jfrOpens,
              Collections.emptySet(),
              Collections.emptyMap());
        }
      }
    }
    return instr;
  }

  @Override
  @CallerSensitive
  public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    try {
      Class<?> caller = Reflection.getCallerClass();
      if (!caller.getName().startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
      }

      Class<?> clz =
          MethodHandles.privateLookupIn(Auxiliary.class, MethodHandles.lookup()).defineClass(code);
      // initialize the class by creating a dummy instance
      clz.getConstructor().newInstance();
      return clz;
    } catch (IllegalAccessException
        | NoSuchMethodException
        | SecurityException
        | InstantiationException
        | InvocationTargetException ignored) {

    }
    return null;
  }

  @Override
  public void newPerfCounter(Object value, String name, String desc) {
    Perf perf = getPerf();
    char tc = desc.charAt(0);
    switch (tc) {
      case 'C':
      case 'Z':
      case 'B':
      case 'S':
      case 'I':
      case 'J':
      case 'F':
      case 'D':
        {
          long initValue = (value != null) ? ((Number) value).longValue() : 0L;
          ByteBuffer b = perf.createLong(name, V_Variable, V_None, initValue);
          b.order(ByteOrder.nativeOrder());
          counters.put(name, b);
        }
        break;

      case '[':
        break;
      case 'L':
        {
          if (desc.equals("Ljava/lang/String;")) {
            byte[] buf;
            if (value != null) {
              buf = getStringBytes((String) value);
            } else {
              buf = new byte[PERF_STRING_LIMIT];
              buf[0] = '\0';
            }
            ByteBuffer b = perf.createByteArray(name, V_Variable, V_String, buf, buf.length);
            counters.put(name, b);
          }
        }
        break;
    }
  }

  @Override
  public ClassLoader getCallerClassLoader(int stackDec) {
    AtomicInteger cont = new AtomicInteger(stackDec);
    AtomicReference<ClassLoader> cl = new AtomicReference<>(null);
    StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .forEach(
            f -> {
              if (cont.getAndDecrement() == 0) {
                cl.compareAndSet(null, f.getDeclaringClass().getClassLoader());
              }
            });
    return cl.get();
  }

  @Override
  public Class<?> getCallerClass(int stackDec) {
    AtomicInteger cont = new AtomicInteger(stackDec);
    AtomicReference<Class<?>> cl = new AtomicReference<>(null);
    StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .forEach(
            f -> {
              if (cont.getAndDecrement() == 0) {
                cl.compareAndSet(null, f.getDeclaringClass());
              }
            });
    return cl.get();
  }

  @Override
  public int version() {
    return Runtime.version().feature();
  }

  @Override
  public JfrEvent.Factory createEventFactory(JfrEvent.Template template) {
    if (!isJfrAvailable()) {
      return null;
    }
    JfrEventFactoryImpl factory = new JfrEventFactoryImpl(template);
    eventFactories.add(factory);
    return factory;
  }

  @Override
  public boolean isBootstrapClass(String className) {
    try {
      return findBootstrapOrNullMtd != null && findBootstrapOrNullMtd.invoke(ClassLoader.getSystemClassLoader(), className) != null;
    } catch (IllegalAccessException | InvocationTargetException ignored) {}
    return false;
  }

  @Override
  protected void cleanupRuntime() {
    if (isJfrAvailable()) {
      for (JfrEventFactoryImpl factory : eventFactories) {
        factory.unregister();
      }
      eventFactories.clear();
    }
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_11.class) {
      if (perf == null) {
        try {
          // Newer JDKs (25+) have removed Perf.GetPerfAction; prefer reflective access to getPerf()
          Method m = Perf.class.getDeclaredMethod("getPerf");
          m.setAccessible(true);
          perf = (Perf) m.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
          // Fallback for older JDKs where GetPerfAction exists
          try {
            Class<?> actClz = Class.forName("jdk.internal.perf.Perf$GetPerfAction");
            Object action = actClz.getDeclaredConstructor().newInstance();
            perf = AccessController.doPrivileged((java.security.PrivilegedAction<Perf>) action);
          } catch (Throwable t) {
            throw new IllegalStateException("Unable to acquire jdk.internal.perf.Perf instance", t);
          }
        }
      }
    }
    return perf;
  }
}
