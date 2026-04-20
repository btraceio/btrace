package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.runtime.IndyDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link HandlerRepository} that resolves probe handler {@link MethodHandle}s
 * via {@link MethodHandles#publicLookup()}.findStatic() on the probe class.
 *
 * <p>Probe handler methods stay in the probe class (per-probe ClassLoader on JDK 8/9-14, or
 * a hidden class on JDK 15+). No bytecode copying is performed.
 *
 * <p><b>Caching:</b> only successful resolutions are cached; failures are returned as
 * {@code null} without poisoning the cache. IndyDispatcher handles transient failure by
 * installing a {@code MutableCallSite} trampoline that retries on each invocation, so a
 * negative cache is not needed — and would in fact defeat the self-healing behaviour.
 */
public final class HandlerRepositoryImpl {
  private static final Logger log = LoggerFactory.getLogger(HandlerRepositoryImpl.class);

  static {
    // Wire up to IndyDispatcher (bootstrap CL) via reflection.
    // IndyDispatcher.repository is set to a method-reference calling our resolveHandler().
    try {
      Class<?> dispatcherClz = Class.forName("org.openjdk.btrace.runtime.IndyDispatcher");
      HandlerRepository hook = HandlerRepositoryImpl::resolveHandler;
      dispatcherClz.getField("repository").set(null, hook);
    } catch (Throwable t) {
      log.warn("Unable to initialize BTrace IndyDispatcher support", t);
    }
  }

  /** Maps probe class name (internal form) → live BTraceProbe instance. */
  private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();

  /**
   * Register a probe after its class has been defined in the JVM. Must be called before
   * any instrumented call site targeting this probe is invoked. If invocation arrives
   * first, {@link org.openjdk.btrace.runtime.IndyDispatcher} installs a self-relinking
   * trampoline that will pick up the probe on its next invocation.
   */
  public static void registerProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.put(probeName, probe);
  }

  /**
   * Unregister a probe. Also invalidates every live {@link java.lang.invoke.MutableCallSite}
   * targeting this probe via {@link IndyDispatcher#invalidateProbe(String)}, swapping their
   * targets to a noop so in-flight and subsequent invocations do not enter probe handler
   * bodies after the associated {@code BTraceRuntime} state has been torn down. This is the
   * dispatch-level equivalent of the older "cushion" approach, which stubbed probe method
   * bodies via bytecode redefine on detach — both exist to keep the instrumented application
   * from crashing when a probe is undeployed while call sites are still live.
   *
   * <p>The handler {@link MethodHandle} cache is per-probe (see {@link BTraceProbe#cacheHandler})
   * so nothing to scan here — the cache dies with the probe object.
   */
  public static void unregisterProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.remove(probeName);
    IndyDispatcher.invalidateProbe(probeName);
  }

  /**
   * Resolve a probe handler MethodHandle. Called from IndyDispatcher.bootstrap() on first
   * execution of each instrumented call site, and subsequently from the trampoline on
   * every retry until resolution succeeds.
   *
   * @param probeName   internal class name of the probe (e.g. {@code "com/example/MyTrace"})
   * @param handlerName handler method name (probe-prefixed, e.g. {@code "MyTrace$onMethod"})
   * @param handlerType the MethodType of the call site
   * @return the resolved MethodHandle, or {@code null} if resolution fails (caller must
   *         treat null as transient and retry)
   */
  public static MethodHandle resolveHandler(
      String probeName, String handlerName, MethodType handlerType) {
    BTraceProbe probe = probeMap.get(probeName);
    if (probe == null) {
      // Probe not registered yet. Do not cache — IndyDispatcher's trampoline will retry.
      log.debug("[INDY] probe {} not registered, probeMap.size={}", probeName, probeMap.size());
      return null;
    }

    MethodHandle cached = probe.getCachedHandler(handlerName, handlerType);
    if (cached != null) {
      log.debug("[INDY] cached handler {} for {}", cached, handlerName);
      return cached;
    }

    Class<?> probeClass = probe.getProbeClass();
    if (probeClass == null) {
      // defineClass has not populated probeClass yet (race with register()).
      // Do not cache — IndyDispatcher's trampoline will retry.
      log.debug("[INDY] probeClass null for {}", probeName);
      return null;
    }

    log.debug("[INDY] resolving {}.{} in {}", probeName, handlerName, probeClass.getClassLoader());

    try {
      // Strip probe-name prefix from handler name (e.g. "MyTrace$onMethod" → "onMethod")
      int dollarIdx = handlerName.lastIndexOf('$');
      String simpleHandlerName =
          dollarIdx >= 0 ? handlerName.substring(dollarIdx + 1) : handlerName;

      MethodHandle mh;
      try {
        // Try public lookup first (works for normal, accessible classes).
        mh = MethodHandles.publicLookup().findStatic(probeClass, simpleHandlerName, handlerType);
      } catch (IllegalAccessException publicLookupFailed) {
        // publicLookup has limited access across classloader boundaries. For probes defined
        // in isolated/unnamed ClassLoaders (per-probe isolation on JDK 8/9-14, or hidden
        // classes on JDK 15+), publicLookup cannot see the handler methods. Fall back to
        // a reflection-based approach: get the method via getDeclaredMethod, then convert
        // it to a MethodHandle using unreflect. This works on all JDK versions.
        log.debug(
            "publicLookup denied access to {}.{}, falling back to reflection-based resolution",
            probeName, simpleHandlerName);
        try {
          // Extract parameter types from the handler MethodType.
          Class<?>[] paramTypes = new Class<?>[handlerType.parameterCount()];
          for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = handlerType.parameterType(i);
          }
          java.lang.reflect.Method method =
              probeClass.getDeclaredMethod(simpleHandlerName, paramTypes);
          method.setAccessible(true);
          mh = MethodHandles.lookup().unreflect(method);
        } catch (Throwable fallbackEx) {
          // Reflection-based approach also failed. Re-throw the original exception from
          // publicLookup so it gets logged with proper context in the outer catch.
          throw publicLookupFailed;
        }
      }

      probe.cacheHandler(handlerName, handlerType, mh);

      if (SharedSettings.GLOBAL.isDumpClasses()) {
        log.debug("BTrace INDY handler resolved: {}.{}", probeName, simpleHandlerName);
      }

      return mh;
    } catch (Throwable e) {
      // Log loudly: unlike transient null-repository or unregistered-probe failures,
      // findStatic exceptions usually mean a real problem (signature mismatch, module
      // access). Don't cache — the trampoline will retry, but the same failure is likely
      // to recur until the probe class/bytecode is fixed.
      log.warn("Failed to resolve handler '{}' in probe '{}'", handlerName, probeName, e);
      return null;
    }
  }
}
