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
package io.btrace.instr;

import io.btrace.core.HandlerRepository;
import io.btrace.core.SharedSettings;
import io.btrace.runtime.BTraceRuntimes;
import io.btrace.runtime.IndyDispatcher;
import io.btrace.runtime.Interval;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link HandlerRepository} that resolves probe handler {@link MethodHandle}s via
 * {@link MethodHandles#publicLookup()}.findStatic() on the probe class.
 *
 * <p>Probe handler methods stay in the probe class (per-probe ClassLoader on JDK 8/9-14, or a
 * hidden class on JDK 15+). No bytecode copying is performed.
 *
 * <p><b>Caching:</b> only successful resolutions are cached; failures are returned as {@code null}
 * without poisoning the cache. IndyDispatcher handles transient failure by installing a {@code
 * MutableCallSite} trampoline that retries on each invocation, so a negative cache is not needed —
 * and would in fact defeat the self-healing behaviour.
 */
public final class HandlerRepositoryImpl {
  private static final Logger log = LoggerFactory.getLogger(HandlerRepositoryImpl.class);

  static {
    // Wire up to IndyDispatcher (bootstrap CL) via reflection.
    // IndyDispatcher.repository is set to a method-reference calling our resolveHandler().
    try {
      Class<?> dispatcherClz = Class.forName("io.btrace.runtime.IndyDispatcher");
      HandlerRepository hook = HandlerRepositoryImpl::resolveHandler;
      dispatcherClz.getField("repository").set(null, hook);
    } catch (Throwable t) {
      log.warn("Unable to initialize BTrace IndyDispatcher support", t);
    }
  }

  /** Maps probe class name (internal form) → live BTraceProbe instance. */
  private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();

  /**
   * Register a probe after its class has been defined in the JVM. Must be called before any
   * instrumented call site targeting this probe is invoked. If invocation arrives first, {@link
   * io.btrace.runtime.IndyDispatcher} installs a self-relinking trampoline that will pick up the
   * probe on its next invocation.
   */
  public static void registerProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.put(probeName, probe);
  }

  /**
   * Unregister a probe. Also invalidates every live {@link java.lang.invoke.MutableCallSite}
   * targeting this probe via {@link IndyDispatcher#invalidateProbe(String)}, swapping their targets
   * to a noop so in-flight and subsequent invocations do not enter probe handler bodies after the
   * associated {@code BTraceRuntime} state has been torn down. This is the dispatch-level
   * equivalent of the older "cushion" approach, which stubbed probe method bodies via bytecode
   * redefine on detach — both exist to keep the instrumented application from crashing when a probe
   * is undeployed while call sites are still live.
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
   * Apply level-based guard to a handler MethodHandle. If the handler has a level requirement
   * (enableAt annotation), wraps the handler in a guard that checks if the current probe level
   * permits execution. This allows the JIT to optimize based on the level and avoids deopt when
   * levels change.
   *
   * @param handler the resolved handler MethodHandle
   * @param probe the BTraceProbe instance
   * @param simpleHandlerName the unqualified handler method name (e.g. "onMethod")
   * @param handlerType the MethodType of the handler
   * @return the handler, possibly wrapped with a level guard
   */
  private static MethodHandle applyLevelGuard(
      MethodHandle handler, BTraceProbe probe, String simpleHandlerName, MethodType handlerType) {
    // Find the OnMethod metadata for this handler
    Level level = null;
    for (OnMethod om : probe.onmethods()) {
      if (om.getMethod().equals(simpleHandlerName)) {
        level = om.getLevel();
        if (level != null) {
          break;
        }
      }
    }

    if (level == null) {
      return handler; // No level guard needed
    }

    // Compose the handler with a level guard. The guard will:
    // 1. Check if current instrumentation level satisfies the requirement
    // 2. If yes, invoke the real handler
    // 3. If no, invoke a noop that returns the default value for the type

    log.debug("Handler {} requires level check: {}", simpleHandlerName, level.getValue());

    try {
      // Create a test MethodHandle that checks the level requirement.
      // It takes the same arguments as the handler but returns boolean.
      MethodType testType = handlerType.changeReturnType(boolean.class);
      MethodHandle levelTest = createLevelCheckMH(level, testType);

      // Create a noop handler that returns default value for the return type
      MethodHandle noop = createNoopMH(handlerType);

      // Compose: if level check passes, invoke handler; otherwise noop
      return MethodHandles.guardWithTest(levelTest, handler, noop);
    } catch (Throwable e) {
      log.warn("Failed to create level guard for handler {}", simpleHandlerName, e);
      return handler; // Fall back to unguarded handler on error
    }
  }

  /**
   * Create a MethodHandle that tests if the current instrumentation level satisfies the given level
   * requirement. Returns true if level check passes, false otherwise.
   */
  private static MethodHandle createLevelCheckMH(Level level, MethodType testType)
      throws Throwable {
    // Get the Interval requirement (e.g., ">=100" → [100, MAX_VALUE])
    Interval interval = level.getValue();

    // Create a static helper method that checks: currentLevel >= a && currentLevel <= b
    // We need to invoke this helper with the level bounds and call the level getter
    // The tricky part: we need access to BTraceRuntime to query the current level,
    // but we don't have it as a parameter. We must create a method that can access it.

    // Use a custom MethodHandle that invokes our level-checking logic
    MethodHandle levelChecker = createLevelCheckerMH(interval);

    // Drop arguments to match testType (accept same params as handler, return boolean)
    return MethodHandles.dropArguments(levelChecker, 0, testType.parameterArray());
  }

  /**
   * Helper: create a MethodHandle that queries BTraceRuntime.getInstrumentationLevel() and checks
   * if it falls within the given interval.
   */
  private static MethodHandle createLevelCheckerMH(Interval interval) throws Throwable {
    int minLevel = interval.getA();
    int maxLevel = interval.getB();

    // Create a MethodHandle that returns true if currentLevel is in [minLevel, maxLevel]
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle checkMH =
        lookup.findStatic(
            HandlerRepositoryImpl.class,
            "checkLevelInRange",
            MethodType.methodType(boolean.class, int.class, int.class, int.class));

    // Bind the interval bounds as arguments at positions 0 and 1
    // After binding: (minLevel=bound, maxLevel=bound, currentLevel=?) -> boolean
    MethodHandle bound = MethodHandles.insertArguments(checkMH, 0, minLevel, maxLevel);
    // After binding: (currentLevel) -> boolean

    // Compose with a getter that queries the current level
    MethodHandle getCurrentLevelMH =
        lookup.findStatic(
            BTraceRuntimes.class, "getCurrentLevel", MethodType.methodType(int.class));
    // getCurrentLevel: () -> int

    // Fold: getCurrentLevel() returns int, pass result as first arg to bound
    // Result: () -> boolean
    return MethodHandles.foldArguments(bound, getCurrentLevelMH);
  }

  /** Static helper invoked via MethodHandle: check if level is in [min, max] range. */
  @SuppressWarnings("unused") // invoked via MethodHandle
  private static boolean checkLevelInRange(int minLevel, int maxLevel, int currentLevel) {
    return currentLevel >= minLevel && currentLevel <= maxLevel;
  }

  /** Create a noop MethodHandle that returns the default value for the given type. */
  private static MethodHandle createNoopMH(MethodType handlerType) throws Throwable {
    Class<?> returnType = handlerType.returnType();
    // Reuse shared default value logic from IndyDispatcher
    Object defaultValue = IndyDispatcher.defaultValueFor(returnType);
    MethodHandle noop = MethodHandles.constant(returnType, defaultValue);
    // Drop arguments so the noop accepts the handler's parameters but ignores them
    return MethodHandles.dropArguments(noop, 0, handlerType.parameterArray());
  }

  /**
   * Resolve a probe handler MethodHandle. Called from IndyDispatcher.bootstrap() on first execution
   * of each instrumented call site, and subsequently from the trampoline on every retry until
   * resolution succeeds.
   *
   * @param probeName internal class name of the probe (e.g. {@code "com/example/MyTrace"})
   * @param handlerName handler method name (probe-prefixed, e.g. {@code "MyTrace$onMethod"})
   * @param handlerType the MethodType of the call site
   * @return the resolved MethodHandle, or {@code null} if resolution fails (caller must treat null
   *     as transient and retry)
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
            probeName,
            simpleHandlerName);
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

      // Apply level guard if this handler has a level check
      mh = applyLevelGuard(mh, probe, simpleHandlerName, handlerType);

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
