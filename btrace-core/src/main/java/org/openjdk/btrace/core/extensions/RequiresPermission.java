package org.openjdk.btrace.core.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an extension requires a specific permission.
 *
 * <p>Extensions must declare all required permissions using this annotation. The BTrace verifier
 * will check that scripts requesting the extension have been granted the necessary permissions.
 *
 * <p>Multiple permissions can be required by repeating this annotation or by using {@link
 * RequiresPermissions}.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * &#64;ExtensionDescriptor(name = "statsd", version = "1.0")
 * &#64;RequiresPermission(value = Permission.NETWORK, reason = "Sends metrics via UDP")
 * &#64;RequiresPermission(value = Permission.THREADS, reason = "Background sender thread")
 * public class StatsdExtension extends Extension {
 *   // ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RequiresPermissions.class)
public @interface RequiresPermission {
  /**
   * The required permission.
   *
   * @return permission
   */
  Permission value();

  /**
   * Human-readable explanation of why this permission is required.
   *
   * @return reason text
   */
  String reason() default "";
}
