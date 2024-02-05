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
    BTraceRuntimeAccess.registerRuntimeAccessor();
  }

  @SuppressWarnings("unchecked")
  private static boolean loadFactory(String clzName) {
    try {
      log.debug("Attempting to load BTrace runtime implementation: {}", clzName);
      Class<BTraceRuntimeImplFactory<?>> factoryClz =
          (Class<BTraceRuntimeImplFactory<?>>)
              ClassLoader.getSystemClassLoader().loadClass(clzName);
      BTraceRuntimeImplFactory<?> instance = factoryClz.getConstructor().newInstance();
      if (instance.isEnabled()) {
        FACTORY = instance;
        log.debug("BTrace runtime implementation {} is loaded", clzName);
        return true;
      }
    } catch (ClassNotFoundException | UnsupportedClassVersionError ignored) {
    } catch (Exception e) {
      log.warn("Failed to load BTrace runtime implementation {}", clzName, e);
    }
    return false;
  }

  public static BTraceRuntime.Impl getDefault() {
    return FACTORY != null ? FACTORY.getDefault() : null;
  }

  public static BTraceRuntime.Impl getCurrent() {
    return BTraceRuntime.getRt();
  }

  public static BTraceRuntime.Impl getRuntime(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    return FACTORY != null ? FACTORY.getRuntime(className, args, cmdListener, inst) : null;
  }
}
