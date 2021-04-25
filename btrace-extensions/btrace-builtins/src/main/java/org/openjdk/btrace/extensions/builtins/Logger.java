package org.openjdk.btrace.extensions.builtins;

import org.openjdk.btrace.core.BTraceContext;

public final class Logger {
    public static void println(String msg) {
        BTraceContext.INSTANCE.sendMsg(msg + System.lineSeparator());
    }

    public static void print(String msg) {
        BTraceContext.INSTANCE.sendMsg(msg);
    }
}
