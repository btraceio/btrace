/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package org.openjdk.btrace.runtime;

import org.openjdk.btrace.core.BTraceRuntimeBridge;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;

/**
 * Bootstrap-visible runtime access shim.
 */
public final class BTraceRuntimeAccess {
  private static volatile Delegate delegate;

  // for testing purposes; needs to be non-final
  private static volatile boolean uniqueClientClassNames = true;

  private BTraceRuntimeAccess() {}

  public static void install(Delegate delegate) {
    BTraceRuntimeAccess.delegate = delegate;
  }

  public static boolean isUniqueClientClassNames() {
    return uniqueClientClassNames;
  }

  public static boolean enter(BTraceRuntimeBridge currentRt) {
    Delegate current = delegate;
    return current != null && current.enter(currentRt);
  }

  public static void leave() {
    Delegate current = delegate;
    if (current != null) {
      current.leave();
    }
  }

  public static BTraceRuntimeBridge forClass(
      Class cl,
      TimerHandler[] tHandlers,
      EventHandler[] evHandlers,
      ErrorHandler[] errHandlers,
      ExitHandler[] eHandlers,
      LowMemoryHandler[] lmHandlers) {
    Delegate current = delegate;
    return current != null
        ? current.forClass(cl, tHandlers, evHandlers, errHandlers, eHandlers, lmHandlers)
        : null;
  }

  public static ThreadLocal newThreadLocal(Object initValue) {
    Delegate current = delegate;
    return current != null ? current.newThreadLocal(initValue) : ThreadLocal.withInitial(() -> initValue);
  }

  public static String getClientName(String forClassName) {
    Delegate current = delegate;
    return current != null ? current.getClientName(forClassName) : forClassName;
  }

  public static ExtensionContext currentContext() {
    Delegate current = delegate;
    return current != null ? current.currentContext() : null;
  }

  public interface Delegate {
    boolean enter(BTraceRuntimeBridge currentRt);

    void leave();

    BTraceRuntimeBridge forClass(
        Class cl,
        TimerHandler[] tHandlers,
        EventHandler[] evHandlers,
        ErrorHandler[] errHandlers,
        ExitHandler[] eHandlers,
        LowMemoryHandler[] lmHandlers);

    ThreadLocal newThreadLocal(Object initValue);

    String getClientName(String forClassName);

    ExtensionContext currentContext();
  }
}
