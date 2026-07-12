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
package io.btrace.instr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forces the JVM's {@code java.lang.invoke} bootstrap machinery (the {@code
 * MethodHandleNatives.linkCallSite -> CallSite.makeSite -> MethodHandle.customize} path shared by
 * every {@code invokedynamic} call site, including lambda expressions) to complete once, on a
 * single thread, as early as possible in agent startup.
 *
 * <p>{@code -javaagent premain()} runs before the target JVM has necessarily finished its own lazy
 * initialization of {@code java.lang.invoke} internals (e.g. {@code MethodHandle$1}). If the agent
 * then starts a second thread (its own server thread) and both threads independently trigger a
 * *first* {@code invokedynamic} linkage concurrently, the JVM can throw {@code
 * ClassCircularityError} racing to initialize the same shared classes. Calling {@link #warmup()} as
 * the very first statement of {@code premain()} — before any other thread is started — forces that
 * shared initialization to happen safely, single-threaded, so later concurrent {@code
 * invokedynamic} use (from the agent's own lambdas or user code) reuses already-initialized classes
 * instead of racing to initialize them.
 */
public final class InvokeBootstrapWarmup {
  private static final Logger log = LoggerFactory.getLogger(InvokeBootstrapWarmup.class);

  private InvokeBootstrapWarmup() {}

  /**
   * Exercises a real {@code invokedynamic} call site (a lambda expression) so the JVM's shared
   * {@code java.lang.invoke} bootstrap classes finish initializing before any other thread can race
   * them. Best-effort: any failure (including a {@link LinkageError} from the very race this is
   * meant to avoid) is caught and logged, never propagated, since callers rely on this running
   * before their own startup logic regardless of outcome.
   */
  public static void warmup() {
    try {
      Runnable r = () -> {};
      r.run();
    } catch (Throwable t) {
      log.debug("java.lang.invoke warm-up failed (best effort)", t);
    }
  }
}
