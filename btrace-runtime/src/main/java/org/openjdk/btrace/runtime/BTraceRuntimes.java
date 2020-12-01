package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;

public final class BTraceRuntimes {
  private static BTraceRuntimeImplFactory<?> FACTORY = null;

  static {
    boolean loaded =
        loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_11$Factory")
            || loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_9$Factory")
            || loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_7$Factory");
    DebugSupport.SHARED.debug("BTraceRuntime loaded: " + loaded);
    BTraceRuntimeAccess.registerRuntimeAccessor();
  }

  @SuppressWarnings("unchecked")
  private static boolean loadFactory(String clzName) {
    try {
      DebugSupport.SHARED.debug("Attempting to load BTrace runtime implementation: " + clzName);
      Class<BTraceRuntimeImplFactory<?>> factoryClz =
          (Class<BTraceRuntimeImplFactory<?>>)
              ClassLoader.getSystemClassLoader().loadClass(clzName);
      BTraceRuntimeImplFactory<?> instance = factoryClz.getConstructor().newInstance();
      if (instance.isEnabled()) {
        FACTORY = instance;
        DebugSupport.SHARED.debug("BTrace runtime implementation " + clzName + " is loaded");
        return true;
      }
    } catch (ClassNotFoundException | UnsupportedClassVersionError ignored) {
    } catch (Exception e) {
      DebugSupport.warning(e);
    }
    return false;
  }

  public static BTraceRuntime.Impl getDefault() {
    return FACTORY != null ? FACTORY.getDefault() : null;
  }

  public static BTraceRuntime.Impl getRuntime(
      String className,
      ArgsMap args,
      CommandListener cmdListener,
      DebugSupport ds,
      Instrumentation inst) {
    return FACTORY != null ? FACTORY.getRuntime(className, args, cmdListener, ds, inst) : null;
  }
}
