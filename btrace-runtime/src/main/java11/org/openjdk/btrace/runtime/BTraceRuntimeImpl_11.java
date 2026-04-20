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
import java.lang.reflect.InaccessibleObjectException;
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
    } catch (NoSuchMethodException | InaccessibleObjectException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  public BTraceRuntimeImpl_11(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    super(className, args, cmdListener, fixExports(inst));

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException | InaccessibleObjectException ignored) {}
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
  public Class<?> defineClass(byte[] code) {
    try {
      // Use StackWalker instead of Reflection.getCallerClass() to avoid
      // CallerSensitive annotation requirement (only works from bootstrap CL)
      Class<?> caller =
          StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
              .walk(frames -> frames.skip(1).findFirst())
              .map(StackWalker.StackFrame::getDeclaringClass)
              .orElse(null);
      if (caller == null || !caller.getName().startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
      }

      if (Runtime.version().feature() >= 15) {
        // Hidden class path: lifetime is tied to Class<?> reachability, so no
        // per-probe ClassLoader is needed. defineHiddenClass(..., initialize=true, ...)
        // also performs class initialization, so we skip the explicit newInstance()
        // that older paths use.
        //
        // Reflective invocation because this source set targets JDK 11 where
        // defineHiddenClass and Lookup.ClassOption do not exist yet.
        MethodHandles.Lookup lookup =
            MethodHandles.privateLookupIn(Auxiliary.class, MethodHandles.lookup());
        // No ClassOption.STRONG: with the default (non-strong) policy the hidden class
        // is unloadable as soon as the Class<?> mirror is no longer strongly reachable
        // from outside the JVM. STRONG would tie its lifetime to the defining loader
        // (i.e. the Auxiliary loader, shared with the agent) which defeats unloading.
        Class<?> classOptionClass =
            Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
        Object optionArray = java.lang.reflect.Array.newInstance(classOptionClass, 0);
        Method defineHidden =
            MethodHandles.Lookup.class.getMethod(
                "defineHiddenClass", byte[].class, boolean.class, optionArray.getClass());
        Object hiddenLookup = defineHidden.invoke(lookup, code, true, optionArray);
        Method lookupClassMtd = hiddenLookup.getClass().getMethod("lookupClass");
        return (Class<?>) lookupClassMtd.invoke(hiddenLookup);
      }

      // JDK 11-14: fall back to per-probe anchor + isolated ClassLoader so the
      // probe class and its defining loader become unreachable on detach.
      // Pass the ClassLoader that can see BTraceUtils and other agent classes.
      ClassLoader parent = BTraceRuntimeImpl_11.class.getClassLoader();
      if (parent == null) {
        // If BTraceRuntimeImpl_11 is in bootstrap, use the current thread's context CL
        parent = Thread.currentThread().getContextClassLoader();
      }
      Class<?> anchor = ProbeAnchor.defineAnchor(parent);
      Class<?> clz =
          MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(code);
      // initialize the class by creating a dummy instance
      clz.getConstructor().newInstance();
      return clz;
    } catch (IllegalAccessException
        | ClassNotFoundException
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
              if (f.getClassName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
                return;
              }
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
              if (f.getClassName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
                return;
              }
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
