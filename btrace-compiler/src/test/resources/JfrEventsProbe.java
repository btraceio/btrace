package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace public class JfrEventsProbe {
    @Event(name = "CustomEvent", label = "Custom Event", fields = "int a, String b")
    private static JfrEvent.Factory customEventFactory;

    @Event(name = "PeriodicEvent", fields = "int cnt", period = "1 s", handler = "onPeriod")
    private static JfrEvent.Factory periodicFactory;

    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        JfrEvent event = prepareEvent(customEventFactory);
        event.withValue("a", 10).withValue("b", "hello").commit();
    }

    public static void onPeriod(JfrEvent event) {
        if (event.shouldCommit()) {
            event.withValue("cnt", 1).commit();
        }
    }

}