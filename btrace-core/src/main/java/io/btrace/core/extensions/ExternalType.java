/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.core.extensions;

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
 * for each declared method. Dispatchers lazily resolve public target methods through {@link
 * java.lang.invoke.MethodHandles#publicLookup()} and cache successful resolutions. Declared erased
 * parameter and return types must exactly match the target member's erased JVM signature; {@code
 * Object} does not coerce a differently typed target signature.
 *
 * <p>Resolution failures are reported as {@link ExternalTypeResolutionException}. Target-method
 * failures propagate unchanged.
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

  /**
   * Marks a direct {@code Object} method return or parameter as an external target type described
   * by another {@link ExternalType} contract. The processor consumes this source-only metadata; the
   * runtime value does not implement the referenced contract interface.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.METHOD, ElementType.PARAMETER})
  @interface Type {
    /** The external contract that supplies the target type name. */
    Class<?> value();
  }

  /**
   * Selects one overloaded target member name for every method in an opt-in group.
   *
   * <p>Apply this annotation, with the same value, to at least two abstract methods in one
   * contract. The methods may use distinct local names; their declared exact signatures select the
   * target overload. This annotation does not perform runtime overload search or coercion.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface Overload {
    String value();
  }

  /**
   * Selects a public target field read. The annotated method must have no target arguments and a
   * non-{@code void} exact field type; {@link Static} selects a static field.
   *
   * <p>The processor consumes this source-only marker. Generated adapters do not inspect it at
   * runtime.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface Getter {
    String value();
  }

  /**
   * Selects a public target field write. The annotated method must return {@code void} and have one
   * exact field-type argument; {@link Static} selects a static field.
   *
   * <p>The processor consumes this source-only marker. Generated adapters do not inspect it at
   * runtime.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface Setter {
    String value();
  }

  /**
   * Selects a public constructor on the adapted type. The annotated method must return direct
   * {@code Object}; its parameters describe the exact constructor signature.
   *
   * <p>The processor consumes this source-only marker. Generated adapters do not inspect it at
   * runtime.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface Constructor {}

  /**
   * Selects {@link Class#isInstance(Object)} for the adapted type. The annotated method must return
   * {@code boolean} and accept one direct {@code Object}; resolution uses the non-null value's
   * defining loader.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface InstanceOf {}

  /**
   * Selects {@link Class#cast(Object)} for the adapted type. The annotated method must return
   * direct {@code Object} and accept one direct {@code Object}; resolution uses the non-null
   * value's defining loader.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  @interface Cast {}
}
