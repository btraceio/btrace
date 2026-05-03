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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.util.Set;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.jfr.JfrEvent;
import sun.misc.Perf;
import sun.misc.Unsafe;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * Helper class used by BTrace built-in functions and also acts runtime "manager" for a specific
 * BTrace client and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
@SuppressWarnings("deprecation")
public final class BTraceRuntimeImpl_8 extends BTraceRuntimeImplBase {
  public static final class Factory extends BTraceRuntimeImplFactory<BTraceRuntimeImpl_8> {
    public Factory() {
      super(new BTraceRuntimeImpl_8());
    }

    @Override
    public BTraceRuntimeImpl_8 getRuntime(
        String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
      return new BTraceRuntimeImpl_8(className, args, cmdListener, inst);
    }

    @Override
    public boolean isEnabled() {
      try {
        Class.forName("java.lang.Module");
        return false;
      } catch (ClassNotFoundException ignored) {
      }
      return true;
    }
  }

  // perf counter variability - we always variable variability
  private static final int V_Variable = 3;
  // perf counter units
  private static final int V_None = 1;
  private static final int V_String = 5;
  private static final int PERF_STRING_LIMIT = 256;

  private final Set<JfrEventFactoryImpl> eventFactories;

  private static Perf perf;

  private final boolean hasJfr;

  private final Method findBootstrapOrNullMtd;

  public BTraceRuntimeImpl_8() {
    boolean jfr = false;
    try {
      Class.forName("jdk.jfr.Event");
      jfr = true;
    } catch (Throwable t) {
    }
    hasJfr = jfr;
    eventFactories = hasJfr ? new java.util.concurrent.CopyOnWriteArraySet<>() : null;

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException | RuntimeException ignored) {
    }
    findBootstrapOrNullMtd = m;
  }

  public BTraceRuntimeImpl_8(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    super(className, args, cmdListener, inst);
    boolean jfr = false;
    try {
      Class.forName("jdk.jfr.Event");
      jfr = true;
    } catch (Throwable t) {
    }
    hasJfr = jfr;
    eventFactories = hasJfr ? new java.util.concurrent.CopyOnWriteArraySet<>() : null;

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException | RuntimeException ignored) {
    }
    findBootstrapOrNullMtd = m;
  }

  @Override
  public Class<?> defineClass(byte[] code) {
    Unsafe unsafe = BTraceRuntime.initUnsafe();
    if (unsafe != null) {
      // Use stack trace instead of Reflection.getCallerClass() to avoid
      // CallerSensitive annotation requirement (only works from bootstrap CL)
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      // stack[0] = getStackTrace, stack[1] = defineClass (this method), stack[2] = caller
      String callerClassName = stack.length > 2 ? stack[2].getClassName() : null;
      if (callerClassName == null || !callerClassName.startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
      }
      // Always define the probe in a fresh, isolated ClassLoader parented to the app CL.
      // This makes the probe class unloadable once the probe's MethodHandles and its
      // BTraceProbe.probeClass reference are cleared on unregister, while allowing the
      // probe to access BTraceUtils and other agent classes.
      ClassLoader parent = BTraceRuntimeImpl_8.class.getClassLoader();
      if (parent == null) {
        parent = Thread.currentThread().getContextClassLoader();
      }
      ClassLoader loader = new ClassLoader(parent) {};
      Class<?> cl = unsafe.defineClass(getClassName(), code, 0, code.length, loader, null);
      unsafe.ensureClassInitialized(cl);
      return cl;
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

  @CallerSensitive
  @Override
  public ClassLoader getCallerClassLoader(int stackDec) {
    Class<?> c = Reflection.getCallerClass(stackDec + 1);
    // Probe handlers run in the bootstrap CL as
    // org.openjdk.btrace.runtime.auxiliary.* classes (INDY dispatch).
    // Skip that frame to find the real application caller, mirroring
    // BTraceRuntimeImpl_9's StackWalker skip of auxiliary.* frames.
    if (c != null && c.getName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
      c = Reflection.getCallerClass(stackDec + 2);
    }
    return c != null ? c.getClassLoader() : null;
  }

  @CallerSensitive
  @Override
  public Class<?> getCallerClass(int stackDec) {
    Class<?> c = Reflection.getCallerClass(stackDec + 1);
    if (c != null && c.getName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
      c = Reflection.getCallerClass(stackDec + 2);
    }
    return c;
  }

  @Override
  public JfrEvent.Factory createEventFactory(JfrEvent.Template template) {
    if (hasJfr) {
      JfrEventFactoryImpl factory = new JfrEventFactoryImpl(template);
      eventFactories.add(factory);
      return factory;
    }
    return () -> JfrEvent.EMPTY;
  }

  @Override
  public boolean isBootstrapClass(String className) {
    try {
      return findBootstrapOrNullMtd != null
          && findBootstrapOrNullMtd.invoke(ClassLoader.getSystemClassLoader(), className) != null;
    } catch (IllegalAccessException | InvocationTargetException ignored) {
    }
    return false;
  }

  @Override
  protected void cleanupRuntime() {
    if (hasJfr) {
      for (JfrEventFactoryImpl factory : eventFactories) {
        factory.unregister();
      }
      eventFactories.clear();
    }
  }

  @Override
  public int version() {
    return 7;
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_8.class) {
      if (perf == null) {
        perf = AccessController.doPrivileged(new Perf.GetPerfAction());
      }
    }
    return perf;
  }
}
