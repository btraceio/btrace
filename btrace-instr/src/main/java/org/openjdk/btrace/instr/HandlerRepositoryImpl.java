package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.core.SharedSettings;
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
 * performed. A sentinel handle ({@link #UNRESOLVED}) is stored for failed lookups so that
 * the lookup is retried on the next bootstrap invocation (after probe re-registration).
 */
public final class HandlerRepositoryImpl {
  private static final Logger log = LoggerFactory.getLogger(HandlerRepositoryImpl.class);

  /** Sentinel stored in cache for failed handler lookups — allows retry after probe reload. */
  private static final MethodHandle UNRESOLVED;

  static {
    try {
      UNRESOLVED =
          MethodHandles.lookup()
              .findStatic(
                  HandlerRepositoryImpl.class,
                  "unresolvedSentinel",
                  MethodType.methodType(void.class));
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }

    // Wire up to IndyDispatcher (bootstrap CL) via reflection.
    // IndyDispatcher.repository is set to a lambda calling our resolveHandler().
    try {
      Class<?> dispatcherClz =
          Class.forName("org.openjdk.btrace.runtime.IndyDispatcher");
      HandlerRepository hook = HandlerRepositoryImpl::resolveHandler;
      dispatcherClz.getField("repository").set(null, hook);
    } catch (UnsupportedClassVersionError ignored) {
      // pre-Java-8 — should not happen since IndyDispatcher compiles to Java 8
    } catch (Throwable t) {
      log.warn("Unable to initialize BTrace IndyDispatcher support", t);
    }
  }

  /** Maps probe class name (internal form) → live BTraceProbe instance. */
  private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();

  /**
   * Maps "{probeName}#{handlerName}{handlerDesc}" → resolved MethodHandle (or UNRESOLVED
   * sentinel).
   */
  private static final Map<String, MethodHandle> handlerCache = new ConcurrentHashMap<>();

  /** Register a probe after its class has been defined in the JVM. */
  public static void registerProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.put(probeName, probe);
    // Evict any UNRESOLVED sentinels that were cached before this probe was registered.
    // This can happen if a bootstrap call raced ahead of registration.
    String prefix = probeName + "#";
    handlerCache.keySet().removeIf(key -> key.startsWith(prefix));
  }

  /** Unregister a probe and clear all cached handles for it. */
  public static void unregisterProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.remove(probeName);
    String prefix = probeName + "#";
    handlerCache.keySet().removeIf(key -> key.startsWith(prefix));
  }

  /**
   * Resolve a probe handler MethodHandle. Called from IndyDispatcher.bootstrap() on first
   * execution of each instrumented call site.
   *
   * @param probeName   internal class name of the probe (e.g. "com/example/MyTrace")
   * @param handlerName handler method name (probe-prefixed, e.g. "MyTrace$onMethod")
   * @param handlerType the MethodType of the call site
   * @return the resolved MethodHandle, or {@code null} if resolution fails
   *         (IndyDispatcher will install a noop in that case)
   */
  public static MethodHandle resolveHandler(
      String probeName, String handlerName, MethodType handlerType) {
    String cacheKey = probeName + "#" + handlerName + handlerType.toMethodDescriptorString();

    MethodHandle cached = handlerCache.get(cacheKey);
    if (cached != null) {
      return cached == UNRESOLVED ? null : cached;
    }

    BTraceProbe probe = probeMap.get(probeName);
    if (probe == null) {
      // Probe not registered yet; store sentinel so we don't hammer the map on every call
      handlerCache.put(cacheKey, UNRESOLVED);
      return null;
    }

    try {
      // The probe script class is always defined in the bootstrap CL (via Unsafe.defineClass with
      // loader=null, because mustBeBootstrap=isTransforming()=true for all transforming probes).
      // probe.getClass() is the BTraceProbeNode/BTraceProbePersisted wrapper in agent CL — we
      // never use that directly; we load the script class from bootstrap CL by name.
      Class<?> probeClass = Class.forName(probeName.replace('/', '.'), false, null);

      // Strip probe-name prefix from handler name (e.g. "MyTrace$onMethod" → "onMethod")
      int dollarIdx = handlerName.lastIndexOf('$');
      String simpleHandlerName = dollarIdx >= 0 ? handlerName.substring(dollarIdx + 1) : handlerName;

      MethodHandle mh =
          MethodHandles.publicLookup().findStatic(probeClass, simpleHandlerName, handlerType);

      handlerCache.put(cacheKey, mh);

      DebugSupport debug = new DebugSupport(SharedSettings.GLOBAL);
      if (debug.isDumpClasses()) {
        log.debug("BTrace INDY handler resolved: {}.{}", probeName, simpleHandlerName);
      }

      return mh;
    } catch (Throwable e) {
      log.warn("Failed to resolve handler '{}' in probe '{}'", handlerName, probeName, e);
      handlerCache.put(cacheKey, UNRESOLVED);
      return null;
    }
  }

  /** Sentinel method — never called directly. */
  private static void unresolvedSentinel() {}
}