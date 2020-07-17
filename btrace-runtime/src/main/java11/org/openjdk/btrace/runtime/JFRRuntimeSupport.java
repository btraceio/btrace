package org.openjdk.btrace.runtime;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class JFRRuntimeSupport {
    private final Set<Runnable> periodicJfrEvents = new CopyOnWriteArraySet<>();
    private final Set<Class<?>> simpleJfrEvents = new CopyOnWriteArraySet<>();

    public void addJfrPeriodicEvent(String eventClassName, String className, String methodName) {
        try {
            System.out.println("*** adding jfr periodic event: " + eventClassName);
            Class<? extends Event> eventClz = (Class<? extends Event>)BTraceRuntime.class.forName(eventClassName);
            Class<?> handlerClass = Class.forName(className);
            final Method handlerMethod = handlerClass.getMethod(methodName, Object.class);
            Runnable hook = (Runnable) Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class[]{Runnable.class}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("run")) {
                        Event event = eventClz.getConstructor().newInstance();
                        Object ret =  handlerMethod.invoke(null, event);
                        return ret;
                    } else {
                        return method.invoke(this, args);
                    }
                }
            });
            FlightRecorder.addPeriodicEvent(eventClz, hook);
            periodicJfrEvents.add(hook);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            StringBuilder msg = new StringBuilder("Unable to register periodic JFR event of type '");
            String eMsg = e.getMessage();
            msg.append(eMsg.replace('/', '.'));
            msg.append("'");
            DebugSupport.info(msg.toString());
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
    }

    public void addJfrEvent(String eventClassName) {
        try {
            Class<? extends Event> eventClz = (Class<? extends Event>) Class.forName(eventClassName);
            FlightRecorder.register(eventClz);
            simpleJfrEvents.add(eventClz);
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
    }

    public void cleanupEvents() {
        for (Runnable hook : periodicJfrEvents) {
            FlightRecorder.removePeriodicEvent(hook);
        }
        for (Class<?> eventClz : simpleJfrEvents) {
            // the holder collection is in the shared impl so can not directly refer the JFR types
            FlightRecorder.unregister((Class<? extends Event>)eventClz);
        }
    }
}
