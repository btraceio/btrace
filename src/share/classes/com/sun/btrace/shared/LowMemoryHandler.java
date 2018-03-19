package com.sun.btrace.shared;

import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class LowMemoryHandler {
    private Method executable;

    public final String method;
    public final String pool;
    public final String thresholdProperty;
    public final long threshold;
    public final boolean trackUsage;

    public LowMemoryHandler(String method, String pool, long threshold, String thresholdProperty, boolean trackUsage) {
        this.method = method;
        this.pool = pool;
        this.threshold = threshold;
        this.thresholdProperty = thresholdProperty;
        this.trackUsage = trackUsage;
    }

    public synchronized Method getMethod(Class clz) throws NoSuchMethodException {
        if (executable == null) {
            executable = trackUsage ? clz.getMethod(method, MemoryUsage.class) : clz.getMethod(method);
        }
        return executable;
    }

    public void invoke(Class clz, Object ... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        getMethod(clz).invoke(clz, null, args);
    }
}
