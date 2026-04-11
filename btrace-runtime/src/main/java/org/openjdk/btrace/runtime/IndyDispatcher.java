/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.openjdk.btrace.core.HandlerRepository;

/**
 * INVOKEDYNAMIC bootstrap class for BTrace probe handler dispatch.
 *
 * <p>Works with Java 8+. Replaces the Java 15-specific {@code Indy} class that used
 * {@code defineHiddenClass}. Probe handler methods stay in the probe class (bootstrap CL)
 * and are resolved via {@link MethodHandles#publicLookup()}.findStatic() — no bytecode
 * copying required.
 *
 * <p>The {@link #repository} field is wired up by {@code HandlerRepositoryImpl}'s static
 * initializer, bridging from the bootstrap classloader (this class) to the agent classloader
 * (HandlerRepositoryImpl).
 *
 * <p><b>Important:</b> This class lives in the bootstrap classloader and must not reference
 * SLF4J or any other logging framework. SLF4J initialization in the bootstrap classloader
 * context can fail, causing this class's static initializer to throw, which prevents the
 * repository from being wired up. Handler resolution failures are logged by
 * {@code HandlerRepositoryImpl.resolveHandler()} on the agent-classloader side.
 */
public final class IndyDispatcher {

  /**
   * Bridge to the HandlerRepository implementation (HandlerRepositoryImpl in agent CL).
   * Set via reflection from HandlerRepositoryImpl's static initializer.
   */
  public static volatile HandlerRepository repository = null;

  /**
   * INVOKEDYNAMIC bootstrap method. Called by the JVM on first execution of each
   * instrumented call site.
   *
   * @param caller   the lookup context of the instrumented class
   * @param name     the handler method name (probe-prefixed, e.g. "probeName$handlerMethod")
   * @param type     the method type of the call site
   * @param probeClassName the binary name of the probe class (passed as BSM constant)
   * @return a {@link ConstantCallSite} wrapping the resolved handler MethodHandle
   */
  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
    MethodHandle mh = null;
    HandlerRepository repo = repository;
    if (repo != null) {
      try {
        mh = repo.resolveHandler(probeClassName, name, type);
      } catch (Throwable t) {
        // Resolution failures are logged on the agent-CL side (HandlerRepositoryImpl).
        // Do not log here: SLF4J must not be referenced from this bootstrap CL class.
      }
    }
    if (mh == null) {
      mh = noop(type);
    }
    return new ConstantCallSite(mh);
  }

  private static MethodHandle noop(MethodType type) {
    try {
      MethodHandle noopHandle =
          MethodHandles.lookup()
              .findStatic(IndyDispatcher.class, "noopImpl", MethodType.methodType(void.class));
      return MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("noop lookup failed", e);
    }
  }

  /** Noop target used when probe is not registered or handler resolution fails. */
  public static void noopImpl() {}
}
