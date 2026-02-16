package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.comm.CommandListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BTraceRuntimes {
  private static final Logger log = LoggerFactory.getLogger(BTraceRuntimes.class);

  private static BTraceRuntimeImplFactory<?> FACTORY = null;

  static {
    boolean loaded =
        loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_11$Factory")
            || loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_9$Factory")
            || loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_8$Factory");
    log.debug("BTraceRuntime loaded: {}", loaded);
    BTraceRuntimeAccessImpl.install();
  }

  private static boolean loadFactory(String className) {
    try {
      log.debug("Attempting to load BTrace runtime implementation: {}", className);
      ClassLoader[] loaders = new ClassLoader[] {
          Thread.currentThread().getContextClassLoader(),
          BTraceRuntimes.class.getClassLoader(),
          ClassLoader.getSystemClassLoader()
      };
      for (ClassLoader loader : loaders) {
        if (loader == null) continue;
        try {
          @SuppressWarnings("unchecked") // generic cast due to dynamic classloading of a known API type
          Class<BTraceRuntimeImplFactory<?>> factoryClz =
              (Class<BTraceRuntimeImplFactory<?>>) loader.loadClass(className);
          BTraceRuntimeImplFactory<?> instance = factoryClz.getConstructor().newInstance();
          if (instance.isEnabled()) {
            FACTORY = instance;
            log.debug("BTrace runtime implementation {} loaded via {}", className, loader);
            return true;
          }
        } catch (ClassNotFoundException | UnsupportedClassVersionError e) {
          // try next loader
          if (log.isDebugEnabled()) {
            log.debug("Loader {} could not load {}: {}", loader, className, e.toString());
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to load BTrace runtime implementation: {}", className, e);
    }
    return false;
  }

  public static BTraceRuntime.Impl getDefault() {
    // Ensure runtime accessor is registered (must be done after static init completes)
    BTraceRuntimeAccessImpl.ensureRegistered(FACTORY);
    return FACTORY != null ? FACTORY.getDefault() : null;
  }

  /**
   * Ensures the runtime accessor is registered in BTraceRuntime.
   * This must be called after getDefault() to complete initialization.
   */
  public static void ensureAccessorRegistered() {
    BTraceRuntimeAccessImpl.ensureRegistered(FACTORY);
  }

  public static BTraceRuntime.Impl getRuntime(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    return FACTORY != null ? FACTORY.getRuntime(className, args, cmdListener, inst) : null;
  }
}
