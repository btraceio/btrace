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
package io.btrace.core;

import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.handlers.ErrorHandler;
import io.btrace.core.handlers.EventHandler;
import io.btrace.core.handlers.ExitHandler;
import io.btrace.core.handlers.LowMemoryHandler;
import io.btrace.core.handlers.TimerHandler;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Bootstrap-visible runtime access shim. */
public final class BTraceRuntimeAccess {
  private static volatile Delegate delegate;

  // for testing purposes; needs to be non-final
  private static volatile boolean uniqueClientClassNames = true;

  // The one agent instance of this JVM, as the entry point a later agent load hands over to.
  private static final AtomicReference<UnaryOperator<String>> agentControl =
      new AtomicReference<>();

  private BTraceRuntimeAccess() {}

  /**
   * Claims this JVM for one agent instance. Every agent load gets a class loader, and with it an
   * agent, of its own, while this class is shared by all of them: a second agent would replace the
   * delegate installed here and break the first one's probes.
   *
   * @param control what a later agent load calls with its argument string; returns one line saying
   *     what was done
   * @return whether this is the first agent instance, which may go on to initialize
   */
  public static boolean claimAgent(UnaryOperator<String> control) {
    return agentControl.compareAndSet(null, control);
  }

  /** The control of the agent instance that holds the claim, or {@code null}. */
  public static UnaryOperator<String> agentControl() {
    return agentControl.get();
  }

  /** Gives the claim back. Only for an instance that failed before installing itself here. */
  public static void releaseAgent(UnaryOperator<String> control) {
    agentControl.compareAndSet(control, null);
  }

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
