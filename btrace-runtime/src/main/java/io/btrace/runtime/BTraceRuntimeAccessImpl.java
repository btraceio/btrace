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
package io.btrace.runtime;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.BTraceRuntimeBridge;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.Command;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.handlers.ErrorHandler;
import io.btrace.core.handlers.EventHandler;
import io.btrace.core.handlers.ExitHandler;
import io.btrace.core.handlers.LowMemoryHandler;
import io.btrace.core.handlers.TimerHandler;
import io.btrace.runtime.auxiliary.Auxiliary;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BTraceRuntimeAccessImpl implements BTraceRuntimeAccess.Delegate {
  private static final Logger log = LoggerFactory.getLogger(BTraceRuntimeAccessImpl.class);
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
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable failure) {
        log.error("BTrace handler execution failed", failure);
      } finally {
        rt = oldRuntime;
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

  /**
   * Ensures the runtime accessor is registered. Must be called after BTraceRuntimes static init
   * completes.
   */
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
   * Look up a registered runtime by probe class name. The name is normalized via {@link
   * #normalizeProbeName}, so hidden-class names with a {@code "/0x..."} suffix resolve to the plain
   * name used at registration.
   */
  static BTraceRuntimeImplBase getRuntime(String probeName) {
    return probeName == null ? null : runtimes.get(normalizeProbeName(probeName));
  }

  /**
   * Drop the {@link BTraceRuntime.Impl} previously registered for {@code className}.
   *
   * <p>Releases the strong GC root the registry map holds on a runtime (and anything it
   * transitively reaches — notably {@code Class<?>}, {@code MethodHandle}s, and its defining {@code
   * ClassLoader}). Callers that create a runtime via {@code BTraceRuntimes.getRuntime(...)} and
   * then abort <strong>must</strong> call this to avoid a permanent Metaspace / object leak.
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
   * Strip the hidden-class suffix from a probe class name so it maps to the registry key used by
   * {@link #addRuntime(String, BTraceRuntimeImplBase)}.
   *
   * <p>On JDK 15+, probes are defined via {@code Lookup.defineHiddenClass}. Hidden classes report
   * their {@link Class#getName()} as {@code "pkg.Name/0xNNNN..."}, where the suffix after the slash
   * is assigned by the VM. The registry is keyed by the plain {@code "pkg.Name"} because that's the
   * name the runtime is registered under, before defineClass runs. Plain class names never contain
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
        current, current.getClassName(), SharedSettings.GLOBAL.getEffectivePermissions());
  }
}
