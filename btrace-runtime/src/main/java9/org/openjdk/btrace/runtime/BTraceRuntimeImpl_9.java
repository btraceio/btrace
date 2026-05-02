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
public final class BTraceRuntimeImpl_9 extends BTraceRuntimeImplBase {
  public static final class Factory extends BTraceRuntimeImplFactory<BTraceRuntimeImpl_9> {
    public Factory() {
      super(new BTraceRuntimeImpl_9());
    }

    @Override
    public BTraceRuntimeImpl_9 getRuntime(
        String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
      return new BTraceRuntimeImpl_9(className, args, cmdListener, inst);
    }

    @Override
    public boolean isEnabled() {
      Runtime.Version version = Runtime.version();
      int major = version.version().get(0);
      return major == 9 || major == 10;
    }
  }
  // perf counter variability - we always variable variability
  private static final int V_Variable = 3;
  // perf counter units
  private static final int V_None = 1;
  private static final int V_String = 5;
  private static final int PERF_STRING_LIMIT = 256;

  private static Perf perf;

  private final Method findBootstrapOrNullMtd;

  public BTraceRuntimeImpl_9() {
    fixExports(BTraceRuntime.instrumentation);

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  public BTraceRuntimeImpl_9(
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
    Set<Module> myModules = Collections.singleton(BTraceRuntimeImpl_9.class.getModule());
    if (instr != null) {
      instr.redefineModule(
          String.class.getModule(),
          Collections.emptySet(),
          Map.of(
              "jdk.internal.reflect", myModules,
              "jdk.internal.perf", myModules),
          Collections.singletonMap("java.lang", myModules),
          Collections.emptySet(),
          Collections.emptyMap());
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

  /**
   * A utility class to load class data in JPMS (Java 9+)
   *
   * @param code class data
   * @return loaded class
   */
  public static Class<?> defineClass(byte[] code) {
    try {
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
    return 9;
  }

  @Override
  public JfrEvent.Factory createEventFactory(JfrEvent.Template template) {
    return () -> JfrEvent.EMPTY;
  }

  @Override
  public boolean isBootstrapClass(String className) {
    try {
      return findBootstrapOrNullMtd != null
          && findBootstrapOrNullMtd.invoke(ClassLoader.getSystemClassLoader(), className) != null;
    } catch (IllegalAccessException | InvocationTargetException ignored) {}
    return false;
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_9.class) {
      if (perf == null) {
        perf = AccessController.doPrivileged(new Perf.GetPerfAction());
      }
    }
    return perf;
  }
}
