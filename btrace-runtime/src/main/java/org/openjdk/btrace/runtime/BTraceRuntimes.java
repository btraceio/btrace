package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;

public final class BTraceRuntimes {
  private static BTraceRuntimeImplFactory<?> FACTORY = null;

  static {
    loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_7$Factory");
    loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_9$Factory");
    loadFactory("org.openjdk.btrace.runtime.BTraceRuntimeImpl_11$Factory");

    BTraceRuntimeAccess.registerRuntimeAccessor();
  }

  @SuppressWarnings("unchecked")
  private static void loadFactory(String clzName) {
    try {
      Class<BTraceRuntimeImplFactory<?>> factoryClz =
          (Class<BTraceRuntimeImplFactory<?>>)
              ClassLoader.getSystemClassLoader().loadClass(clzName);
      BTraceRuntimeImplFactory<?> instance = factoryClz.getConstructor().newInstance();
      if (instance.isEnabled()) {
        FACTORY = instance;
      }
    } catch (ClassNotFoundException | UnsupportedClassVersionError e) {
      DebugSupport.info("Can not load runtime factory: " + clzName);
    } catch (Exception e) {
      DebugSupport.warning(e);
    }
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
