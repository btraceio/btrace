/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Bridge interface between the bootstrap-resident {@link
 * org.openjdk.btrace.indy.IndyDispatcher} and the agent-resident handler
 * repository. The implementation lives in the agent classloader and is set on
 * {@code IndyDispatcher.repository} during agent initialization.
 */
public interface HandlerRepository {
  /**
   * Resolve a probe handler to a {@link MethodHandle}.
   *
   * @param probeName the fully qualified probe class name
   * @param handlerName the prefixed handler method name
   * @param handlerType the method type of the handler
   * @return a MethodHandle to the handler, or {@code null} if resolution fails
   */
  MethodHandle resolveHandler(String probeName, String handlerName, MethodType handlerType);

  /**
   * Resolve a runtime utility method (e.g. {@code MethodTracker.hit}) to a {@link MethodHandle}.
   *
   * @param owner the internal name of the owning class
   * @param name the method name
   * @param type the method type
   * @return a MethodHandle to the method, or {@code null} if resolution fails
   */
  MethodHandle resolveRuntime(String owner, String name, MethodType type);

  /**
   * Returns the instrumentation level set for the given probe, or {@code Integer.MIN_VALUE} if the
   * probe is not registered or has no level set.
   *
   * @param probeName the fully qualified probe class name
   */
  int getLevel(String probeName);

  /**
   * Initialises the BTrace runtime for the given probe class by constructing handler arrays from
   * pre-registered metadata and calling {@code BTraceRuntimeAccess.forClass}.
   *
   * <p>Called from the probe's {@code <clinit>} via an {@code INVOKEDYNAMIC "initRuntime"} call
   * site routed through {@link org.openjdk.btrace.indy.IndyDispatcher#runtimeBootstrap}.
   *
   * @param probeClass the probe class (resolved at link time from the LDC constant in clinit)
   * @return the {@code BTraceRuntimeBridge} instance as {@code Object} (avoids bootstrap-visible
   *     reference to the masked class)
   */
  Object initProbeRuntime(Class<?> probeClass);
}
