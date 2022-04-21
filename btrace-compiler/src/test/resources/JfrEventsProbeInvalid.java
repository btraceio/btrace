package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Jfr.*;

@BTrace public class JfrEventsProbeInvalid {
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
        setEventField(event, "c", "world");
        commit(event);
    }

    @PeriodicEvent(name = "PeriodicEvent", fields = @Event.Field(type = Event.FieldType.INT, name = "cnt", kind = @Event.Field.Kind(name = Event.FieldKind.TIMESTAMP)), period = "1 s")
    public static void onPeriod(JfrEvent event) {
        if (shouldCommit(event)) {
            setEventField(event, "sum", 1);
            commit(event);
        }
    }

}