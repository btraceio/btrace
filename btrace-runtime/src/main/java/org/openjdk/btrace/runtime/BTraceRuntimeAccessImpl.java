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
 */

package org.openjdk.btrace.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.BTraceRuntimeBridge;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;

public final class BTraceRuntimeAccessImpl implements BTraceRuntimeAccess.Delegate {
  private static final BTraceRuntimeAccessImpl INSTANCE = new BTraceRuntimeAccessImpl();

  static final class RTWrapper {
    private BTraceRuntime.Impl rt = null;

    boolean set(BTraceRuntime.Impl other) {
      if (rt != null && other != null) {
        return false;
      }
      rt = other;
      return true;
    }

    <T> T escape(Callable<T> c) {
      BTraceRuntime.Impl oldRuntime = rt;
      rt = null;
      try {
        return c.call();
      } catch (Exception ignored) {
      } finally {
        if (oldRuntime != null) {
          rt = oldRuntime;
        }
      }
      return null;
    }
  }

  static final class Accessor implements BTraceRuntime.BTraceRuntimeAccessor {
    @Override
    public BTraceRuntime.Impl getRt() {
      BTraceRuntime.Impl current = getCurrent();
      return current != null ? current : dummy;
    }
  }

  // to be registered by BTraceRuntimeImpl implementation class
  // should be treated as virtually immutable
  private static volatile BTraceRuntime.Impl dummy = null;

  protected static final ThreadLocal<RTWrapper> rt;

  static {
    rt = ThreadLocal.withInitial(RTWrapper::new);
  }

  // BTraceRuntime against BTrace class name
  protected static final Map<String, BTraceRuntimeImplBase> runtimes = new ConcurrentHashMap<>();

  // a set of all the client names connected so far
  private static final Set<String> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();

  // BTrace Class object corresponding to this client; accessed from instrumented code
  private Class clazz;

  // instrumentation level field for each runtime; accessed from instrumented code
  private Field level;

  private final AtomicBoolean exitting = new AtomicBoolean(false);

  private BTraceRuntimeAccessImpl() {}

  public static void install() {
    BTraceRuntimeAccess.install(INSTANCE);
    // Register the runtime accessor immediately
    // This triggers BTraceRuntimes class load if not already loaded, which sets up FACTORY
    BTraceRuntimes.ensureAccessorRegistered();
  }

  /** Ensures the runtime accessor is registered. Must be called after BTraceRuntimes static init completes. */
  public static void ensureRegistered(BTraceRuntimeImplFactory<?> factory) {
    if (dummy == null) {
      registerRuntimeAccessor(factory);
    }
  }

  private static boolean isRuntimeAccessorRegistered() {
    return dummy != null;
  }

  static void addRuntime(String className, BTraceRuntimeImplBase rt) {
    runtimes.put(className, rt);
  }

  /**
   * Look up a registered runtime by probe class name. The name is normalized via
   * {@link #normalizeProbeName}, so hidden-class names with a
   * {@code "/0x..."} suffix resolve to the plain name used at registration.
   */
  static BTraceRuntimeImplBase getRuntime(String probeName) {
    return probeName == null ? null : runtimes.get(normalizeProbeName(probeName));
  }

  /**
   * Drop the {@link BTraceRuntime.Impl} previously registered for {@code className}.
   *
   * <p>Releases the strong GC root the registry map holds on a runtime (and anything it
   * transitively reaches — notably {@code Class<?>}, {@code MethodHandle}s, and its
   * defining {@code ClassLoader}). Callers that create a runtime via
   * {@code BTraceRuntimes.getRuntime(...)} and then abort <strong>must</strong> call this
   * to avoid a permanent Metaspace / object leak.
   */
  static void removeRuntime(String className) {
    runtimes.remove(className);
  }

  /** Enter method is called by every probed method just before the probe actions start. */
  static boolean enterInternal(BTraceRuntime.Impl currentRt) {
    BTraceRuntimeImplBase current = (BTraceRuntimeImplBase) currentRt;
    if (current.isDisabled()) return false;
    return rt.get().set(current);
  }

  static void leaveInternal() {
    rt.get().set(null);
  }

  static String getClientNameInternal(String forClassName) {
    int idx = forClassName.lastIndexOf('/');
    if (idx > -1) {
      forClassName =
          Auxiliary.class.getPackage().getName().replace('.', '/')
              + "/"
              + forClassName.substring(idx + 1);
    } else {
      forClassName = Auxiliary.class.getPackage().getName().replace('.', '/') + "/" + forClassName;
    }

    if (!BTraceRuntimeAccess.isUniqueClientClassNames()) {
      return forClassName;
    }

    String name = forClassName;
    int suffix = 1;
    while (clients.contains(name)) {
      name = forClassName + "$" + (suffix++);
    }
    clients.add(name);
    return name;
  }

  void shutdownCmdLine() {
    exitting.set(true);
  }

  /**
   * One instance of BTraceRuntime is created per-client. This forClass method creates it. Class
   * passed is the preprocessed BTrace program of the client.
   */
  static BTraceRuntimeImplBase forClassInternal(
      Class cl,
      TimerHandler[] tHandlers,
      EventHandler[] evHandlers,
      ErrorHandler[] errHandlers,
      ExitHandler[] eHandlers,
      LowMemoryHandler[] lmHandlers) {
    BTraceRuntimeImplBase runtime = runtimes.get(normalizeProbeName(cl.getName()));
    runtime.init(cl, tHandlers, evHandlers, errHandlers, eHandlers, lmHandlers);
    return runtime;
  }

  /**
   * Strip the hidden-class suffix from a probe class name so it maps to the
   * registry key used by {@link #addRuntime(String, BTraceRuntimeImplBase)}.
   *
   * <p>On JDK 15+, probes are defined via {@code Lookup.defineHiddenClass}. Hidden
   * classes report their {@link Class#getName()} as {@code "pkg.Name/0xNNNN..."},
   * where the suffix after the slash is assigned by the VM. The registry is keyed
   * by the plain {@code "pkg.Name"} because that's the name the runtime is
   * registered under, before defineClass runs. Plain class names never contain
   * a {@code '/'}, so stripping from the first slash is safe on all paths.
   */
  static String normalizeProbeName(String rawName) {
    int slash = rawName.indexOf('/');
    return slash > 0 ? rawName.substring(0, slash) : rawName;
  }

  /**
   * Utility to create a new ThreadLocal object. Called by preprocessed BTrace class to create
   * ThreadLocal for each @TLS variable. Called from instrumented code.
   *
   * @param initValue Initial value. This value must be either a boxed primitive or {@linkplain
   *     Cloneable}. In case a {@linkplain Cloneable} value is provided the value is never used
   *     directly - instead, a new clone of the value is created per thread.
   */
  static ThreadLocal newThreadLocalInternal(Object initValue) {
    return ThreadLocal.withInitial(
        () -> {
          if (initValue == null) return initValue;

          if (initValue instanceof Cloneable) {
            try {
              Class<?> clz = initValue.getClass();
              Method m = clz.getDeclaredMethod("clone");
              m.setAccessible(true);
              return m.invoke(initValue);
            } catch (Exception e) {
              System.err.println("BTrace: Failed to clone TLS initial value: " + e.getMessage());
              return null;
            }
          }
          return initValue;
        });
  }

  /** Get the current thread BTraceRuntime instance if there is one. */
  static BTraceRuntimeImplBase getCurrent() {
    // During initialization, dummy may not be set yet - return null gracefully
    if (!isRuntimeAccessorRegistered()) {
      return null;
    }
    RTWrapper rtw = rt.get();
    BTraceRuntime.Impl current = rtw != null ? rtw.rt : null;
    current = current != null ? current : dummy;
    return (BTraceRuntimeImplBase) current;
  }

  @SuppressWarnings("UnusedReturnValue")
  static <T> T doWithCurrent(Callable<T> callable) {
    RTWrapper rtw = rt.get();
    assert rtw != null : "BTraceRuntime access not set up";
    return rtw.escape(callable);
  }

  void send(String msg) {
    BTraceRuntimeImplBase rt = getCurrent();
    if (rt != null) {
      rt.send(msg);
    }
  }

  void send(Command cmd) {
    BTraceRuntimeImplBase rt = getCurrent();
    if (rt != null) {
      rt.send(cmd);
    }
  }

  static void registerRuntimeAccessor(BTraceRuntimeImplFactory<?> factory) {
    try {
      // Get the default runtime from the factory (passed to avoid circular dependency)
      dummy = factory != null ? factory.getDefault() : null;
      Field fld = BTraceRuntime.class.getDeclaredField("rtAccessor");
      fld.setAccessible(true);
      fld.set(null, new Accessor());
    } catch (IllegalAccessException
        | IllegalArgumentException
        | NoSuchFieldException
        | SecurityException e) {
      System.err.println("BTrace: Failed to register runtime accessor: " + e.getMessage());
    }
  }

  @Override
  public boolean enter(BTraceRuntimeBridge currentRt) {
    if (!(currentRt instanceof BTraceRuntime.Impl)) {
      return false;
    }
    return enterInternal((BTraceRuntime.Impl) currentRt);
  }

  @Override
  public void leave() {
    BTraceRuntimeAccessImpl.leaveInternal();
  }

  @Override
  public BTraceRuntimeBridge forClass(
      Class cl,
      TimerHandler[] tHandlers,
      EventHandler[] evHandlers,
      ErrorHandler[] errHandlers,
      ExitHandler[] eHandlers,
      LowMemoryHandler[] lmHandlers) {
    return BTraceRuntimeAccessImpl.forClassInternal(
        cl, tHandlers, evHandlers, errHandlers, eHandlers, lmHandlers);
  }

  @Override
  public ThreadLocal newThreadLocal(Object initValue) {
    return BTraceRuntimeAccessImpl.newThreadLocalInternal(initValue);
  }

  @Override
  public String getClientName(String forClassName) {
    return BTraceRuntimeAccessImpl.getClientNameInternal(forClassName);
  }

  @Override
  public ExtensionContext currentContext() {
    RTWrapper wrapper = rt.get();
    BTraceRuntimeImplBase current = wrapper != null ? (BTraceRuntimeImplBase) wrapper.rt : null;
    if (current == null) return null;
    return new ExtensionContextImpl(
        current,
        current.getClassName(),
        SharedSettings.GLOBAL.getEffectivePermissions());
  }
}
