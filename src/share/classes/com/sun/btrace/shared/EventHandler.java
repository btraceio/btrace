package com.sun.btrace.shared;

import java.lang.reflect.Method;

public final class EventHandler {
    public final String method;
    public final String event;

    public EventHandler(String method, String event) {
        this.method = method;
        this.event = event;
    }

    public Method getMethod(Class clz) throws NoSuchMethodException {
        return clz.getMethod(method);
    }
}
