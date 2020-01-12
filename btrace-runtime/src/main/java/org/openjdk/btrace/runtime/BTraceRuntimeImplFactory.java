package org.openjdk.btrace.runtime;

import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;

import java.lang.instrument.Instrumentation;

public abstract class BTraceRuntimeImplFactory<T extends BTraceRuntime.Impl> {
    private final T defaultRuntime;

    protected BTraceRuntimeImplFactory(T defaultRuntime) {
        this.defaultRuntime = defaultRuntime;
    }

    public final T getDefault() {
        return defaultRuntime;
    }

    public abstract T getRuntime(String className, ArgsMap args, CommandListener cmdListener, DebugSupport ds, Instrumentation inst);

    public abstract boolean isEnabled();
}
