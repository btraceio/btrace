package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Jfr.*;

@BTrace public class JfrEventsProbe {
    @Event(
        name = "CustomEvent",
        label = "Custom Event",
        fields = {
            @Event.Field(type = Event.FieldType.INT, name = "a"),
            @Event.Field(type = Event.FieldType.STRING, name = "b")
        }
    )
    private static JfrEvent.Factory customEventFactory;

    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        JfrEvent event = prepareEvent(customEventFactory);
        setEventField(event, "a", 10);
        setEventField(event, "b", "hello");
        commit(event);
    }

    @PeriodicEvent(name = "PeriodicEvent", fields = @Event.Field(type = Event.FieldType.INT, name = "cnt", kind = @Event.Field.Kind(name = Event.FieldKind.TIMESTAMP)), period = "1 s")
    public static void onPeriod(JfrEvent event) {
        if (shouldCommit(event)) {
            setEventField(event, "cnt", 1);
            commit(event);
        }
    }

}