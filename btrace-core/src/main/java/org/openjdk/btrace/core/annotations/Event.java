package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *     <code>
 * \@Event(name="myCustomEvent", fields="int f1, java.lang.String f2, boolean f3")
 * private static JfrEvent myCustomEvent = null;
 *
 * ...
 * \@OnMethod(...)
 * public static void onprobe() {
 *     JfrEvent event = BTraceUtils.prepareEvent(myCustomEvent);
 *     if (event.shouldCommit()) {
 *       event
 *         .withValue("f1", 10)
 *         .withValue("f2", "hello")
 *         .withValue(2, false)
 *         .commit();
 *     }
 * }
 *     </code>
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    String name();
    String label() default "";
    String description() default "";
    String[] category() default "";
    String fields();
    String period() default "";
    String handler() default "";
}
