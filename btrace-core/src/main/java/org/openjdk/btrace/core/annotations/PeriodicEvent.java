package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;

/**
 * Mark a method as a JFR periodic event handler.
 *
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
   *
   * @return event name
   * @see Name#value()
   */
  String name();

  /**
   * Event label
   *
   * @return event label
   * @see Label#value()
   */
  String label() default "";

  /**
   * Event description
   *
   * @return event description
   * @see Description#value()
   */
  String description() default "";

  /**
   * Event category
   *
   * @return event category
   * @see Category#value()
   */
  String[] category() default "";

  /**
   * Event fields
   *
   * @return event field definitions
   */
  Event.Field[] fields();

  /**
   * Event period
   *
   * @return event period
   * @see Period#value()
   */
  String period() default "eachChunk";
}
