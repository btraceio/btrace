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
   * by another {@link ExternalType} contract. The processor consumes this source-only metadata;
   * the runtime value does not implement the referenced contract interface.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.METHOD, ElementType.PARAMETER})
  @interface Type {
    /** The external contract that supplies the target type name. */
    Class<?> value();
  }
}
