package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *     <code>
 * \@PeriodicEvent(name="periodicEvent", fields="boolean hit", period="eachChunk", handler="periodicEventHandler")
 * private static JfrEvent periodicEvent;
 *
 * ...
 * \@PeriodicEventCallback("periodicEvent")
 * public static void periodicEventHandler(JfrEvent periodic) {
 *     periodic.withValue(0, true).commit();
 * }
 *     </code>
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeriodicEvent {
    String name();
    String label() default "";
    String description() default "";
    String[] category() default "";
    String fields();
    String period() default "eachChunk";
    String handler();
}
