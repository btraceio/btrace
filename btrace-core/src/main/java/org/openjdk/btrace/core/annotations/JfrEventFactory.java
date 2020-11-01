package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *     <code>
 * @JfrEventFactory(name="myCustomEvent", fields="int f1, java.lang.String f2, boolean f3")
 * private static JfrEventFactoryImpl customEventFactory;
 *
 * @JfrEventFactory(name="periodicEvent", fields="boolean hit")
 * private static JfrEventFactoryImpl periodicEventFactory;
 * ...
 * @OnMethod(...)
 * public static void onprobe() {
 *     JfrEvent event = customEventFactory.prepare();
 *     if (event.shouldCommit()) {
 *       event
 *         .withValue("f1", 10)
 *         .withValue("f2", "hello")
 *         .withValue(2, false)
 *         .commit();
 *     }
 * }
 *
 * @PeriodicEventCallback("periodicEvent")
 * public static void periodicEventHandler() {
 *     periodicEventFactory.prepare().withValue(0, true).commit();
 * }
 *     </code>
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JfrEventFactory {
    String name();
    String label() default "";
    String description() default "";
    String[] category() default "";
    String fields();
    String period() default "";
}
