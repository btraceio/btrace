package org.openjdk.btrace.core.handlers;

import java.lang.reflect.Method;

public final class EventHandler {
    public static final String ALL_EVENTS = "";

    public final String method;
    private final String event;

    public EventHandler(String method, String event) {
        this.method = method;
        this.event = event;
    }

    public String getEvent() {
        return event != null ? event : ALL_EVENTS;
    }

    public Method getMethod(Class clz) throws NoSuchMethodException {
        return clz.getMethod(method);
    }
}
