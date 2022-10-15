package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * Mark a field as a custom event factory.
 *
 * <pre>
 *     <code>
 * \@Event(name="myCustomEvent", fields={|@Event.Field(type = FieldType.INT, name = "f1"), |@Event.Field(type = FieldType.STRING, name = "f2"), |@Event.Field(type = FieldType.BOOLEAN, name = "f3")})
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
 *
 * @since 2.1.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
  /** Event field type */
  enum FieldType {
    BYTE("byte"),
    CHAR("char"),
    SHORT("short"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    BOOLEAN("boolean"),
    STRING("java.lang.String"),
    CLASS("java.lang.Class"),
    THREAD("java.lang.Thread");

    private final String type;

    FieldType(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }

  /** Field kind */
  enum FieldKind {
    /**
     * @see jdk.jfr.Timestamp
     */
    TIMESTAMP,
    /**
     * @see jdk.jfr.Timespan
     */
    TIMESPAN,
    /**
     * @see jdk.jfr.DataAmount
     */
    DATAAMOUNT,
    /**
     * @see jdk.jfr.Frequency
     */
    FREQUENCY,
    /**
     * @see jdk.jfr.MemoryAddress
     */
    MEMORYADDRESS,
    /**
     * @see jdk.jfr.Percentage
     */
    PERCENTAGE,
    /**
     * @see jdk.jfr.BooleanFlag
     */
    BOOLEANFLAG,
    /**
     * @see jdk.jfr.Unsigned
     */
    UNSIGNED,
    /** No additional field kind specification */
    NONE
  }

  /** Event field definition */
  @interface Field {
    /** Additional field kind */
    @interface Kind {
      FieldKind name();

      String value() default "";
    }

    /**
     * @return field type
     */
    FieldType type();

    /**
     * @return field name
     */
    String name();

    /**
     * @return field label
     */
    String label() default "";

    /**
     * @return field description
     */
    String description() default "";

    /**
     * @return additional field kind
     */
    Kind kind() default @Kind(name = FieldKind.NONE);
  }

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
   * Each event should have a stacktrace associated with it
   *
   * @return {@literal true} if each event should have a stacktrace associated with it
   */
  boolean stacktrace() default true;

  /**
   * Event fields
   *
   * @return event field definitions
   */
  Field[] fields();
}
