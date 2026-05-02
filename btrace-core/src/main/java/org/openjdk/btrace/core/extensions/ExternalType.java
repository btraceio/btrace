package org.openjdk.btrace.core.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as the build-time contract for an <em>external</em> application type — one
 * whose class is not available at the extension's compile/link time and must be resolved at run
 * time via the application's class loader.
 *
 * <p>When combined with the {@code @ExternalType} annotation processor, a companion adapter class
 * named {@code <InterfaceSimpleName>$Ext} is generated in the same package with static dispatchers
 * for each declared method. Dispatchers lazily resolve the target class and method via {@link
 * java.lang.invoke.MethodHandles#publicLookup()} and cache the resulting {@link
 * java.lang.invoke.MethodHandle}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExternalType {
  /** Fully-qualified name of the external type this interface adapts. */
  String value();

  /**
   * Marks an interface method as a static method on the external type. Without this annotation,
   * methods are resolved with {@code findVirtual} and dispatched against the receiver passed as the
   * first adapter argument.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface Static {}
}
