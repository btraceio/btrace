/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
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
import jdk.jfr.EventType;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.jfr.JfrEvent;
import org.openjdk.btrace.runtime.aux.Auxilliary;

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
        String className,
        ArgsMap args,
        CommandListener cmdListener,
        DebugSupport ds,
        Instrumentation inst) {
      return new BTraceRuntimeImpl_11(className, args, cmdListener, ds, inst);
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

  private final Set<JfrEventFactoryImpl> eventFactories = new java.util.concurrent.CopyOnWriteArraySet<>();

  public BTraceRuntimeImpl_11() {}

  public BTraceRuntimeImpl_11(
      String className,
      ArgsMap args,
      CommandListener cmdListener,
      DebugSupport ds,
      Instrumentation inst) {
    super(className, args, cmdListener, ds, fixExports(inst));
  }

  private static Instrumentation fixExports(Instrumentation instr) {
    Set<Module> myModules = Collections.singleton(BTraceRuntimeImpl_11.class.getModule());
    if (instr != null) {
      instr.redefineModule(
          String.class.getModule(),
          Collections.emptySet(),
          Map.of(
              "jdk.internal.reflect", myModules,
              "jdk.internal.perf", myModules),
          Map.of(
                  "java.lang", myModules
          ),
          Collections.emptySet(),
          Collections.emptyMap());
      instr.redefineModule(
          EventType.class.getModule(),
          Collections.emptySet(),
          Map.of(
                  "jdk.jfr.internal", myModules),
          Map.of(
                  "jdk.jfr", myModules
          ),
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
          MethodHandles.privateLookupIn(Auxilliary.class, MethodHandles.lookup()).defineClass(code);
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
    JfrEventFactoryImpl factory = new JfrEventFactoryImpl(template, debug);
    eventFactories.add(factory);
    return factory;
  }

  @Override
  protected void cleanupRuntime() {
    for (JfrEventFactoryImpl factory : eventFactories) {
      factory.unregister();
    }
    eventFactories.clear();
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_11.class) {
      if (perf == null) {
        perf = AccessController.doPrivileged(new Perf.GetPerfAction());
      }
    }
    return perf;
  }
}
