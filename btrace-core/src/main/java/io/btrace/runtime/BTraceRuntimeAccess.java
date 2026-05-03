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
package io.btrace.runtime;

import io.btrace.core.BTraceRuntimeBridge;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.handlers.ErrorHandler;
import io.btrace.core.handlers.EventHandler;
import io.btrace.core.handlers.ExitHandler;
import io.btrace.core.handlers.LowMemoryHandler;
import io.btrace.core.handlers.TimerHandler;

/** Bootstrap-visible runtime access shim. */
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
    return current != null
        ? current.newThreadLocal(initValue)
        : ThreadLocal.withInitial(() -> initValue);
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
