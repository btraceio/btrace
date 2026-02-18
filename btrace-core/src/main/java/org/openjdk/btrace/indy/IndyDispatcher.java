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

package org.openjdk.btrace.indy;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.runtime.LinkingFlag;

/**
 * Minimal invokedynamic bootstrap dispatcher for BTrace probe handlers. Resides in the bootstrap
 * classloader. All actual handler resolution is delegated to {@link HandlerRepository}, which is set
 * by the agent at initialization time.
 *
 * <p>Instrumented bytecode emits {@code INVOKEDYNAMIC} instructions targeting the {@link
 * #bootstrap} method. On first invocation, the bootstrap resolves a {@link MethodHandle} to the
 * probe handler (living in the agent classloader) and returns a {@link ConstantCallSite} so
 * subsequent calls go directly through the cached handle.
 *
 * <p>This class, together with {@link org.openjdk.btrace.runtime.LinkingFlag}, forms the minimal
 * bootstrap footprint required by BTrace.
 */
public final class IndyDispatcher {
  private static final MethodHandle NOOP;

  static {
    try {
      NOOP =
          MethodHandles.lookup()
              .findStatic(IndyDispatcher.class, "noop", MethodType.methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Set by {@code HandlerRepositoryImpl} during agent initialization. Bridges the bootstrap
   * classloader to the agent classloader where probe handlers are loaded.
   */
  public static volatile HandlerRepository repository;

  /**
   * Bootstrap method for probe handler dispatch. Called by the JVM when an {@code INVOKEDYNAMIC}
   * instruction targeting a probe handler is first executed.
   *
   * @param caller lookup context provided by the JVM
   * @param name the call site name (prefixed handler method name)
   * @param type the method type of the call site
   * @param probeClassName the fully qualified probe class name
   * @return a {@link ConstantCallSite} wrapping the resolved handler, or a no-op fallback
   */
  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
    LinkingFlag.guardLinking();
    try {
      MethodHandle mh;
      try {
        mh = repository.resolveHandler(caller.lookupClass().getName(), probeClassName, name, type);
      } catch (Throwable t) {
        mh = null;
      }
      if (mh == null) {
        mh = MethodHandles.dropArguments(NOOP, 0, type.parameterArray());
      }
      return new ConstantCallSite(mh);
    } finally {
      LinkingFlag.reset();
    }
  }

  /**
   * Bootstrap method for runtime utility calls (e.g. {@code MethodTracker}). Called by the JVM when
   * an {@code INVOKEDYNAMIC} instruction targeting a runtime utility method is first executed.
   *
   * @param caller lookup context provided by the JVM
   * @param name the method name to resolve
   * @param type the method type of the call site
   * @param owner the internal name of the owning class (e.g. {@code
   *     "org/openjdk/btrace/instr/MethodTracker"})
   * @return a {@link ConstantCallSite} wrapping the resolved method, or a no-op fallback
   */
  public static CallSite runtimeBootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String owner) {
    LinkingFlag.guardLinking();
    try {
      MethodHandle mh;
      try {
        mh = repository.resolveRuntime(owner, name, type);
      } catch (Throwable t) {
        mh = null;
      }
      if (mh == null) {
        mh = buildDefaultHandle(type);
      }
      return new ConstantCallSite(mh);
    } finally {
      LinkingFlag.reset();
    }
  }

  /** No-op handler used as fallback when handler resolution fails. */
  public static void noop() {}

  private static MethodHandle buildDefaultHandle(MethodType type) {
    Class<?> ret = type.returnType();
    MethodHandle base;
    if (ret == void.class) {
      base = NOOP;
    } else if (ret == boolean.class) {
      base = MethodHandles.constant(boolean.class, false);
    } else if (ret == int.class) {
      base = MethodHandles.constant(int.class, 0);
    } else if (ret == long.class) {
      base = MethodHandles.constant(long.class, 0L);
    } else {
      // Reference types or other primitives - return null/zero via identity tricks
      base = MethodHandles.constant(ret, defaultValue(ret));
    }
    return MethodHandles.dropArguments(base, 0, type.parameterArray());
  }

  private static Object defaultValue(Class<?> type) {
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == char.class) return (char) 0;
    if (type == float.class) return 0.0f;
    if (type == double.class) return 0.0d;
    return null; // reference types
  }

  private IndyDispatcher() {}
}
