package com.sun.btrace.shared;

import java.lang.reflect.Method;

public final class TimerHandler {
    public final String method;
    public final long period;

    public TimerHandler(String method, long period) {
        this.method = method;
        this.period = period;
    }

    public Method getMethod(Class clz) throws NoSuchMethodException {
        return clz.getMethod(method);
    }
}
