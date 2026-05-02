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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BTraceRuntimeAccess {
  private static final Logger log = LoggerFactory.getLogger(BTraceRuntimeAccess.class);

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
    registerRuntimeAccessor();
    // ignore
  }

  // for testing purposes; needs to be non-final
  private static volatile boolean uniqueClientClassNames = true;

  // BTraceRuntime against BTrace class name
  protected static final Map<String, BTraceRuntimeImplBase> runtimes = new ConcurrentHashMap<>();

  // a set of all the client names connected so far
  private static final Set<String> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();

  // BTrace Class object corresponding to this client; accessed from instrumented code
  private Class clazz;

  // instrumentation level field for each runtime; accessed from instrumented code
  private Field level;

  private final AtomicBoolean exitting = new AtomicBoolean(false);

  static void addRuntime(String className, BTraceRuntimeImplBase rt) {
    runtimes.put(className, rt);
  }

  /** Enter method is called by every probed method just before the probe actions start. */
  public static boolean enter(BTraceRuntime.Impl currentRt) {
    BTraceRuntimeImplBase current = (BTraceRuntimeImplBase) currentRt;
    if (current.isDisabled()) return false;
    return rt.get().set(current);
  }

  public static void leave() {
    rt.get().set(null);
  }

  /**
   * Returns the current ExtensionContext for the executing BTrace script, or null if none. Used by
   * the invokedynamic bootstrap to construct runtime-aware services.
   */
  public static ExtensionContext currentContext() {
    RTWrapper wrapper = rt.get();
    BTraceRuntimeImplBase current = wrapper != null ? (BTraceRuntimeImplBase) wrapper.rt : null;
    if (current == null) return null;
    return new ExtensionContextImpl(
        current, current.getClassName(), SharedSettings.GLOBAL.getEffectivePermissions());
  }

  public static String getClientName(String forClassName) {
    int idx = forClassName.lastIndexOf('/');
    if (idx > -1) {
      forClassName =
          Auxiliary.class.getPackage().getName().replace('.', '/')
              + "/"
              + forClassName.substring(idx + 1);
    } else {
      forClassName = Auxiliary.class.getPackage().getName().replace('.', '/') + "/" + forClassName;
    }

    if (!uniqueClientClassNames) {
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

  public void shutdownCmdLine() {
    exitting.set(true);
  }

  /**
   * One instance of BTraceRuntime is created per-client. This forClass method creates it. Class
   * passed is the preprocessed BTrace program of the client.
   */
  public static BTraceRuntimeImplBase forClass(
      Class cl,
      TimerHandler[] tHandlers,
      EventHandler[] evHandlers,
      ErrorHandler[] errHandlers,
      ExitHandler[] eHandlers,
      LowMemoryHandler[] lmHandlers) {
    BTraceRuntimeImplBase runtime = runtimes.get(cl.getName());
    runtime.init(cl, tHandlers, evHandlers, errHandlers, eHandlers, lmHandlers);
    return runtime;
  }

  /**
   * Utility to create a new ThreadLocal object. Called by preprocessed BTrace class to create
   * ThreadLocal for each @TLS variable. Called from instrumented code.
   *
   * @param initValue Initial value. This value must be either a boxed primitive or {@linkplain
   *     Cloneable}. In case a {@linkplain Cloneable} value is provided the value is never used
   *     directly - instead, a new clone of the value is created per thread.
   */
  public static ThreadLocal newThreadLocal(Object initValue) {
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
              log.warn("Failed to clone TLS initial value", e);
              return null;
            }
          }
          return initValue;
        });
  }

  /** Get the current thread BTraceRuntime instance if there is one. */
  static BTraceRuntimeImplBase getCurrent() {
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

  public void send(String msg) {
    BTraceRuntimeImplBase rt = getCurrent();
    if (rt != null) {
      rt.send(msg);
    }
  }

  public void send(Command cmd) {
    BTraceRuntimeImplBase rt = getCurrent();
    if (rt != null) {
      rt.send(cmd);
    }
  }

  static void registerRuntimeAccessor() {
    try {
      dummy = BTraceRuntimes.getDefault();
      Field fld = BTraceRuntime.class.getDeclaredField("rtAccessor");
      fld.setAccessible(true);
      fld.set(null, new Accessor());
    } catch (IllegalAccessException
        | IllegalArgumentException
        | NoSuchFieldException
        | SecurityException e) {
      log.warn("Failed to register runtime accessor", e);
    }
  }
}
