package org.openjdk.btrace.core.annotations;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a field as a custom event factory.
 *
 * <pre>
 *     <code>
 * \@Event(name="myCustomEvent", fields="int f1, java.lang.String f2, boolean f3")
 * private static JfrEvent myCustomEvent = null;
 *
 * ...
 * \@OnMethod(...)
 * public static void onprobe() {
 *     JfrEvent event = BTraceUtils.Jfr.prepareEvent(myCustomEvent);
 *     if (event.shouldCommit()) {
 *       BTraceUtils.Jfr.setEventField(event, "f1", 10);
 *       BTraceUtils.Jfr.setEventField(event, "f2", "hello");
 *       BTraceUtils.Jfr.setEventField(event, 2, false);
 *       BTraceUtils.Jfr.commit(event);
 *     }
 * }
 *     </code>
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    /**
     * Event name
     * @return event name
     * @see Name#value()
     */
    String name();

    /**
     * Event label
     * @return event label
     * @see Label#value()
     */
    String label() default "";

    /**
     * Event description
     * @return event description
     * @see Description#value()
     */
    String description() default "";

    /**
     * Event category
     * @return event category
     * @see Category#value()
     */
    String[] category() default "";

    /**
     * Each event should have a stacktrace associated with it
     * @return {@literal true} if each event should have a stacktrace associated with it
     */
    boolean stacktrace() default true;

    /**
     * Comma separated list of event field definitions<br>
     * Event field definition is in form of '[type] [name]', eg. {@literal int x}<br>
     * The following are allowed types:
     * <ul>
     *     <li>byte</li>
     *     <li>char</li>
     *     <li>short</li>
     *     <li>int</li>
     *     <li>long</li>
     *     <li>float</li>
     *     <li>double</li>
     *     <li>boolean</li>
     *     <li>string</li>
     * </ul>
     * @return comma separated list of event field definitions
     */
    String fields();
}
