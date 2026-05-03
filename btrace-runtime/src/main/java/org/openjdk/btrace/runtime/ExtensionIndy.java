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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.annotations.InjectionMode;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.extension.ExtensionBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invokedynamic bootstrap methods for BTrace extension access. Provides classloader bridging
 * between BTrace scripts and extensions.
 */
public final class ExtensionIndy {
  private static final Logger logger = LoggerFactory.getLogger(ExtensionIndy.class);

  /**
   * Bridge to extension system. Set by ExtensionBridgeImpl during agent initialization. Must be
   * volatile for visibility across threads.
   */
  public static volatile ExtensionBridge bridge = null;

  static {
    logger.info("ExtensionIndy loaded");
  }

  /**
   * Bootstrap method for extension service field access. Called when a script accesses an @Injected
   * field for the first time.
   *
   * @param caller caller's lookup context
   * @param fieldName field name (for debugging)
   * @param type method type - should be {@code () -> ServiceInterface}
   * @param serviceClassName fully qualified service class name
   * @param serviceType legacy service type (ignored; retained for compatibility)
   * @param factoryMethod optional factory method name (empty string if none)
   * @return CallSite that returns the extension service instance
   */
  public static CallSite bootstrapFieldGet(
      MethodHandles.Lookup caller,
      String fieldName,
      MethodType type,
      String serviceClassName,
      String serviceType,
      String factoryMethod,
      Integer optionalFlag,
      String injectMode)
      throws Exception {
    MethodHandle mh;
    boolean isOptional = optionalFlag != 0;
    InjectionMode parsedMode = parseInjectionMode(injectMode);
    InjectionMode effectiveMode = resolveMode(parsedMode);
    logger.debug("linking: mode={}", effectiveMode);

    boolean linkLog = isLinkLogEnabled();
    try {
      Class<?> serviceClass = requireServiceClass(serviceClassName);
      Object instance = createServiceInstance(serviceClass, factoryMethod, serviceClassName);
      mh = MethodHandles.constant(type.returnType(), instance);
      if (linkLog)
        logger.info("Linked service '{}' to '{}'", serviceClassName, serviceClass.getName());

    } catch (Throwable t) {
      logger.debug("Bootstrap failed for '{}': {}", serviceClassName, t.toString());
      // Decide fallback behavior based on optional flag and injection mode.
      // In SHIM mode we allow shim fallback even for non-optional injections so scripts can run
      // with no-ops when implementations are blocked by permissions.
      boolean allowShim = (effectiveMode == InjectionMode.SHIM) || isOptional;
      if (allowShim) {
        Object fallback = resolvePreGeneratedShim(type, caller, effectiveMode);
        if (fallback == null) {
          fallback = createFallbackInstance(effectiveMode, type, caller, t);
        }
        if (linkLog)
          logger.warn(
              "Service '{}' unavailable; using {} fallback",
              serviceClassName,
              (effectiveMode == InjectionMode.SHIM ? "SHIM" : "THROW"));
        mh = MethodHandles.constant(type.returnType(), fallback);
      } else {
        throw t instanceof Exception ? (Exception) t : new Exception(t);
      }
    }

    try {
      mh = mh.asType(type);
    } catch (java.lang.invoke.WrongMethodTypeException ignored) {
    }
    return new ConstantCallSite(mh);
  }

  private static Object resolvePreGeneratedShim(
      MethodType type, MethodHandles.Lookup caller, InjectionMode mode) {
    try {
      Class<?> iface = type.returnType();
      ClassLoader cl = iface.getClassLoader();
      ShimTarget target = ShimIndex.resolve(iface, cl);
      if (target == null) return null;
      String fqcn = (mode == InjectionMode.SHIM ? target.noopFqcn : target.throwFqcn);
      if (fqcn == null || fqcn.isEmpty()) return null;
      ClassLoader loader = (cl != null ? cl : ClassLoader.getSystemClassLoader());
      Class<?> shim = loader.loadClass(fqcn);
      java.lang.reflect.Field f = shim.getField("INSTANCE");
      Object inst = f.get(null);
      if (iface.isInstance(inst)) return inst;
    } catch (Throwable ignore) {
    }
    return null;
  }

  private static InjectionMode resolveMode(InjectionMode fieldMode) {
    if (fieldMode != null && fieldMode != InjectionMode.UNSPECIFIED) {
      return fieldMode;
    }
    String prop = System.getProperty("btrace.extension.shimMode", "throw");
    if ("shim".equalsIgnoreCase(prop)) return InjectionMode.SHIM;
    return InjectionMode.THROW;
  }

  private static InjectionMode parseInjectionMode(String injectMode) {
    if (injectMode == null || injectMode.isEmpty()) return InjectionMode.UNSPECIFIED;
    if ("THROW".equalsIgnoreCase(injectMode)) return InjectionMode.THROW;
    if ("SHIM".equalsIgnoreCase(injectMode)) return InjectionMode.SHIM;
    return InjectionMode.UNSPECIFIED;
  }

  private static boolean isLinkLogEnabled() {
    return SharedSettings.GLOBAL.isDebug() || Boolean.getBoolean("btrace.extension.linkLog");
  }

  private static Class<?> requireServiceClass(String serviceClassName) throws Exception {
    if (bridge == null) {
      throw new IllegalStateException("ExtensionIndy.bridge not initialized");
    }
    Class<?> serviceClass = bridge.getExtensionClass(serviceClassName);
    if (serviceClass == null) {
      throw new ClassNotFoundException("Extension service not found: " + serviceClassName);
    }
    if (serviceClass.isInterface()) {
      throw new IllegalStateException(
          "No implementation available for service (interface returned): " + serviceClassName);
    }
    logger.debug("Resolved service '{}' to '{}'", serviceClassName, serviceClass.getName());
    return serviceClass;
  }

  private static MethodHandle findInstantiationHandle(Class<?> serviceClass, String factoryMethod)
      throws Exception {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    boolean useFactory = factoryMethod != null && !factoryMethod.isEmpty();
    try {
      if (useFactory) {
        return lookup.findStatic(serviceClass, factoryMethod, MethodType.methodType(serviceClass));
      }
      return lookup.findConstructor(serviceClass, MethodType.methodType(void.class));
    } catch (Throwable t) {
      logger.debug(
          "Instantiation handle resolution failed for '{}': {}",
          serviceClass.getName(),
          t.toString());
      throw new IllegalStateException(
          "Unable to create service instance for " + serviceClass.getName(), t);
    }
  }

  private static Object createServiceInstance(
      Class<?> serviceClass, String factoryMethod, String serviceClassName) throws Exception {
    MethodHandle mh = findInstantiationHandle(serviceClass, factoryMethod);
    Object instance;
    try {
      instance = mh.invokeWithArguments();
    } catch (Throwable t) {
      throw new IllegalStateException("Unable to instantiate service " + serviceClassName, t);
    }
    if (instance instanceof Extension) {
      Extension ext = (Extension) instance;
      ExtensionContext ctx = BTraceRuntimeAccess.currentContext();
      if (ctx == null) {
        throw new IllegalStateException("Extension context not available for " + serviceClassName);
      }
      ext.initialize(ctx);
      tryRegisterExtension(ctx, ext);
    }
    return instance;
  }

  private static void tryRegisterExtension(ExtensionContext ctx, Extension ext) {
    try {
      Method m = ctx.getClass().getDeclaredMethod("registerExtension", Extension.class);
      m.setAccessible(true);
      m.invoke(ctx, ext);
    } catch (Throwable ignore) {
      // best effort; closing will be skipped if registration fails
    }
  }

  private static Object createFallbackInstance(
      InjectionMode mode, MethodType type, MethodHandles.Lookup caller, Throwable cause)
      throws Exception {
    Class<?> iface = type.returnType();
    if (!iface.isInterface()) {
      return null;
    }
    ClassLoader loader = iface.getClassLoader();
    if (loader == null) {
      loader = ClassLoader.getSystemClassLoader();
    }
    InvocationHandler handler;
    if (mode == InjectionMode.SHIM) {
      handler = new NoOpHandler();
    } else {
      handler = new ThrowingHandler(iface.getName(), cause);
    }
    Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] {iface}, handler);
    return proxy;
  }

  private static final class NoOpHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      int paramCount = method.getParameterCount();
      if (name.equals("toString") && paramCount == 0)
        return "NoOp" + proxy.getClass().getInterfaces()[0].getSimpleName();
      if (name.equals("hashCode") && paramCount == 0) return System.identityHashCode(proxy);
      if (name.equals("equals") && paramCount == 1) return proxy == args[0];
      Class<?> rt = method.getReturnType();
      if (rt == void.class) return null;
      if (rt.isPrimitive()) {
        if (rt == boolean.class) return false;
        if (rt == byte.class) return (byte) 0;
        if (rt == short.class) return (short) 0;
        if (rt == char.class) return (char) 0;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == float.class) return 0f;
        if (rt == double.class) return 0d;
      }
      return null;
    }
  }

  private static final class ThrowingHandler implements InvocationHandler {
    private final String iface;
    private final Throwable cause;

    ThrowingHandler(String iface, Throwable cause) {
      this.iface = iface;
      this.cause = cause;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      int paramCount = method.getParameterCount();
      if (name.equals("toString") && paramCount == 0)
        return "Throwing" + proxy.getClass().getInterfaces()[0].getSimpleName();
      if (name.equals("hashCode") && paramCount == 0) return System.identityHashCode(proxy);
      if (name.equals("equals") && paramCount == 1) return proxy == args[0];
      IllegalStateException ex =
          new IllegalStateException("BTrace optional service unavailable: " + iface, cause);
      throw ex;
    }
  }

  private ExtensionIndy() {
    // Utility class, no instances
  }

  private static final class ShimTarget {
    final String noopFqcn;
    final String throwFqcn;

    ShimTarget(String noopFqcn, String throwFqcn) {
      this.noopFqcn = noopFqcn;
      this.throwFqcn = throwFqcn;
    }
  }

  private static final class ShimIndex {
    private static volatile java.util.Map<String, ShimTarget> cache;

    static ShimTarget resolve(Class<?> iface, ClassLoader cl) {
      String key = iface.getName();
      java.util.Map<String, ShimTarget> map = cache;
      if (map == null) {
        synchronized (ShimIndex.class) {
          if (cache == null) {
            cache = load(cl);
          }
          map = cache;
        }
      }
      return map.get(key);
    }

    private static java.util.Map<String, ShimTarget> load(ClassLoader cl) {
      java.util.Map<String, ShimTarget> map = new java.util.HashMap<>();
      ClassLoader loader = ExtensionIndy.class.getClassLoader();
      if (loader == null) loader = ClassLoader.getSystemClassLoader();
      java.io.InputStream is = loader.getResourceAsStream("META-INF/btrace/shims.index");
      if (is == null) return map;
      try (java.io.BufferedReader br =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty() || line.startsWith("#")) continue;
          int eq = line.indexOf('=');
          if (eq <= 0) continue;
          String k = line.substring(0, eq).trim();
          String rest = line.substring(eq + 1).trim();
          String noop = rest;
          String thrw = null;
          int comma = rest.indexOf(',');
          if (comma > 0) {
            noop = rest.substring(0, comma).trim();
            thrw = rest.substring(comma + 1).trim();
          }
          map.put(k, new ShimTarget(noop, thrw));
        }
      } catch (Throwable ignore) {
        // ignore; will fallback to dynamic proxy
      }
      return map;
    }
  }
}
