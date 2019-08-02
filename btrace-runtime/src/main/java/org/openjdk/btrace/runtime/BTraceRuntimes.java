package org.openjdk.btrace.runtime;


import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;

import java.lang.instrument.Instrumentation;
import java.util.ServiceLoader;

public final class BTraceRuntimes {
    public static BTraceRuntime.Impl getDefault() {
        for (BTraceRuntimeImplFactory<?> factory : ServiceLoader.load(BTraceRuntimeImplFactory.class)) {
            if (factory.isEnabled()) {
                return factory.getDefault();
            }
        }
        return null;
    }

    public static BTraceRuntime.Impl getRuntime(String className, ArgsMap args, CommandListener cmdListener, DebugSupport ds, Instrumentation inst) {
        for (BTraceRuntimeImplFactory<?> factory : ServiceLoader.load(BTraceRuntimeImplFactory.class)) {
            if (factory.isEnabled()) {
                return factory.getRuntime(className, args, cmdListener, ds, inst);
            }
        }
        return null;
    }
}
