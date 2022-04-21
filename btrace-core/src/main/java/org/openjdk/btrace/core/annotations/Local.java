package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark the probe method argument as a probe-local variable.<br>
 * <br>
 *
 * <p>A probe-local variable lives in the scope of the instrumented method and is referencable by
 * its name.
 *
 * <p>Any probe handler method can lookup a probe-local variable by its name and read it or modify
 * it (if marked as 'mutable'). The modification is done 'in-place' - eg. assigning a new value to
 * the probe-local value will actually change the value for all other readers.
 *
 * <pre>
 * public static void handler(@Local(name = "var", mutable = true) int variable) {
 *   // the value will be updated and any subsequent read of that variable will see the new value
 *   variable = variable + 1;
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Local {
  /**
   * The probe-local variable name<br>
   * Within the same instrumented method a same-named variable will refer to the same value. The
   * values of the same named probe-local variables may differ across instrumented methods.
   *
   * @return the variable name
   */
  String name();

  /**
   * Marks the probe-local variable as mutation capable<br>
   * If a probe-local variable does not set this attribute to {@literal true} all attempts to modify
   * the value of that variable will be prevented by BTrace verifier.
   *
   * @return {@literal true} if mutation capable; {@literal false} otherwise
   */
  boolean mutable() default false;
}
