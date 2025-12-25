package org.openjdk.btrace.core.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes metadata for a BTrace extension.
 *
 * <p>This annotation must be present on all {@link Extension} subclasses. It provides information
 * about the extension's identity, version, and dependencies.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * &#64;ExtensionDescriptor(
 *   name = "statsd",
 *   version = "1.0",
 *   description = "StatsD metrics client",
 *   minBTraceVersion = "2.1"
 * )
 * &#64;RequiresPermission(Permission.NETWORK)
 * &#64;RequiresPermission(Permission.THREADS)
 * public class StatsdExtension extends Extension {
 *   // ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionDescriptor {
  /**
   * The unique name of this extension.
   *
   * @return extension name
   */
  String name();

  /**
   * The version of this extension.
   *
   * @return version string (e.g., "1.0", "2.1.0")
   */
  String version();

  /**
   * Human-readable description of this extension.
   *
   * @return description text
   */
  String description() default "";

  /**
   * Minimum BTrace version required by this extension.
   *
   * @return minimum BTrace version (e.g., "2.1")
   */
  String minBTraceVersion() default "";

  /**
   * Other extensions that must be available for this extension to function.
   *
   * @return array of required extension classes
   */
  Class<? extends Extension>[] dependencies() default {};
}
