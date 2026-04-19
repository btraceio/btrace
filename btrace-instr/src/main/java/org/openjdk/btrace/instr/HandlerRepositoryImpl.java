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
 * <p>Probe handler methods stay in the probe class (bootstrap CL). No bytecode copying is
 * performed.
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

  /** Maps (probeName, handlerName, MethodType) → resolved MethodHandle. */
  private static final Map<HandlerKey, MethodHandle> handlerCache = new ConcurrentHashMap<>();

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
   * Unregister a probe and clear all cached handles for it. Also invalidates every live
   * {@link java.lang.invoke.MutableCallSite} targeting this probe via
   * {@link IndyDispatcher#invalidateProbe(String)}, swapping their targets to a noop so
   * in-flight and subsequent invocations do not enter probe handler bodies after the
   * associated {@code BTraceRuntime} state has been torn down. This is the dispatch-level
   * equivalent of the older "cushion" approach, which stubbed probe method bodies via
   * bytecode redefine on detach — both exist to keep the instrumented application from
   * crashing when a probe is undeployed while call sites are still live.
   */
  public static void unregisterProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.remove(probeName);
    handlerCache.keySet().removeIf(k -> k.probe.equals(probeName));
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
    HandlerKey cacheKey = new HandlerKey(probeName, handlerName, handlerType);

    MethodHandle cached = handlerCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    BTraceProbe probe = probeMap.get(probeName);
    if (probe == null) {
      // Probe not registered yet. Do not cache — IndyDispatcher's trampoline will retry.
      return null;
    }
    Class<?> probeClass = probe.getProbeClass();
    if (probeClass == null) {
      // defineClass has not populated probeClass yet (race with register()).
      // Do not cache — IndyDispatcher's trampoline will retry.
      return null;
    }

    try {
      // Strip probe-name prefix from handler name (e.g. "MyTrace$onMethod" → "onMethod")
      int dollarIdx = handlerName.lastIndexOf('$');
      String simpleHandlerName =
          dollarIdx >= 0 ? handlerName.substring(dollarIdx + 1) : handlerName;

      MethodHandle mh =
          MethodHandles.publicLookup().findStatic(probeClass, simpleHandlerName, handlerType);

      handlerCache.put(cacheKey, mh);

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

  private static final class HandlerKey {
    final String probe;
    final String handler;
    final MethodType type;
    private final int hash;

    HandlerKey(String probe, String handler, MethodType type) {
      this.probe = probe;
      this.handler = handler;
      this.type = type;
      this.hash = (probe.hashCode() * 31 + handler.hashCode()) * 31 + type.hashCode();
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof HandlerKey)) return false;
      HandlerKey k = (HandlerKey) o;
      return hash == k.hash
          && probe.equals(k.probe)
          && handler.equals(k.handler)
          && type.equals(k.type);
    }
  }
}
