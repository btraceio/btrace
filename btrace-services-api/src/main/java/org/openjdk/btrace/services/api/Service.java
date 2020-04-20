/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.services.api;

import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.services.spi.RuntimeService;
import org.openjdk.btrace.services.spi.SimpleService;

/**
 * A service factory to be used to get access to the services locally inside one particular method.
 * The instantiated services are referenced as local variables.
 *
 * <p>For script-wide service instantiation use {@linkplain Injected} annotation.
 *
 * @author Jaroslav Bachorik
 */
public final class Service {
  /**
   * Creates a BTrace runtime aware service
   *
   * @param <S> service type
   * @param clz service class
   * @return The instance of service of the required type
   */
  public static <S extends RuntimeService> S runtime(Class<S> clz) {
    throw new IllegalStateException("BTrace service injection failed");
  }

  /**
   * Creates a simple service via the default 0-argument constructor
   *
   * @param <S> service type
   * @param clz service class
   * @return The instance of service of the required type
   */
  public static <S extends SimpleService> S simple(Class<S> clz) {
    throw new IllegalStateException("BTrace service injection failed");
  }

  /**
   * Creates a simple service via the provided factory method
   *
   * @param <S> service type
   * @param factoryMethod factory method to use; it must be a static method declared by the service
   *     class
   * @param clz service class
   * @return The instance of service of the required type
   */
  public static <S extends SimpleService> S simple(String factoryMethod, Class<S> clz) {
    throw new IllegalStateException("BTrace service injection failed");
  }
}
