package org.openjdk.btrace.core.annotations;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a method as a JFR periodic event handler.
 * <pre>
 *     <code>
 * \@PeriodicEvent(name="periodicEvent", fields="boolean hit", period="eachChunk")
 * public static void periodicEventHandler(JfrEvent periodic) {
 *     BTraceUtils.Jfr.setEventField(periodic, "hit", true);
 *     BTraceUtils.Jfr.commit(periodic);
 * }
 *     </code>
 * </pre>
 *
 * @since 2.1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PeriodicEvent {
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

    /**
     * Event period
     * @return event period
     * @see Period#value()
     */
    String period() default "eachChunk";
}
