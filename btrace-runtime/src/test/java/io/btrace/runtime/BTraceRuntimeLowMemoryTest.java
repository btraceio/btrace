/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.handlers.LowMemoryHandler;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** {@code @OnLowMemory} handling in the runtime, driven without real memory pressure. */
class BTraceRuntimeLowMemoryTest {
  private static final MemoryUsage USAGE = new MemoryUsage(0, 2048, 4096, 8192);

  /** Probe stand-in whose handler takes a parameter the runtime cannot supply. */
  public static final class WrongSignatureProbe {
    public static void onLowMemory(String notAMemoryUsage) {}
  }

  /** Probe stand-in that counts its handler's calls. */
  public static final class CountingProbe {
    static final AtomicInteger calls = new AtomicInteger();

    public static void onLowMemory(MemoryUsage usage) {
      calls.incrementAndGet();
    }
  }

  @Test
  void handlerIsNotCalledWhenTheRuntimeRefusesEntry() throws Exception {
    CountingProbe.calls.set(0);
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8("low-memory-disabled-test", new ArgsMap(), command -> {}, null);
    LowMemoryHandler handler = new LowMemoryHandler("onLowMemory", "Metaspace", 1, null, true);
    runtime.init(CountingProbe.class, null, null, null, null, new LowMemoryHandler[] {handler});

    onWorkerThread(() -> runtime.runLowMemoryHandler(handler, USAGE));
    assertEquals(1, CountingProbe.calls.get(), "the handler did not run on an enabled runtime");

    runtime.handleExit(0);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!runtime.isDisabled() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertTrue(runtime.isDisabled(), "the runtime was not disabled after exit");
    onWorkerThread(() -> runtime.runLowMemoryHandler(handler, USAGE));
    assertEquals(1, CountingProbe.calls.get(), "the handler ran although enter() refused");
  }

  @Test
  void unknownPoolIsReportedWithTheAvailablePools() throws Exception {
    String warning = startAndAwaitWarning("No Such Pool");
    assertTrue(warning.contains("'No Such Pool'"), warning);
    assertTrue(warning.contains("Metaspace"), "available pools not listed: " + warning);
  }

  @Test
  void poolWithoutUsageThresholdIsReported() throws Exception {
    String pool = null;
    for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
      if (!bean.isUsageThresholdSupported()) {
        pool = bean.getName();
        break;
      }
    }
    assumeTrue(pool != null, "every memory pool of this JVM supports usage thresholds");
    String warning = startAndAwaitWarning(pool);
    assertTrue(warning.contains("'" + pool + "'"), warning);
    assertTrue(warning.contains("does not support usage thresholds"), warning);
    assertTrue(warning.contains("Metaspace"), "pools with thresholds not listed: " + warning);
  }

  @Test
  void exitRestoresThePoolThresholdTheApplicationHadSet() throws Exception {
    MemoryPoolMXBean metaspace = null;
    for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
      if (bean.getName().equals("Metaspace")) {
        metaspace = bean;
      }
    }
    assumeTrue(metaspace != null && metaspace.isUsageThresholdSupported(), "no Metaspace pool");
    long original = metaspace.getUsageThreshold();
    long applicationThreshold = 123_456_789L;
    long probeThreshold = 1L << 40; // far above any real Metaspace usage: no notification
    metaspace.setUsageThreshold(applicationThreshold);
    try {
      BTraceRuntimeImpl_8 runtime =
          new BTraceRuntimeImpl_8("low-memory-restore-test", new ArgsMap(), command -> {}, null);
      LowMemoryHandler handler =
          new LowMemoryHandler("onLowMemory", "Metaspace", probeThreshold, null, true);
      runtime.init(CountingProbe.class, null, null, null, null, new LowMemoryHandler[] {handler});
      onWorkerThread(runtime::start);
      assertEquals(probeThreshold, metaspace.getUsageThreshold(), "the probe did not arm the pool");

      onWorkerThread(() -> runtime.handleExit(0));
      assertEquals(
          applicationThreshold,
          metaspace.getUsageThreshold(),
          "the probe's exit did not restore the application's threshold");
    } finally {
      metaspace.setUsageThreshold(original);
    }
  }

  /** Starts a runtime whose one handler watches {@code pool}; returns the warning it sends. */
  private static String startAndAwaitWarning(String pool) throws Exception {
    List<Command> commands = Collections.synchronizedList(new ArrayList<Command>());
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8("low-memory-pool-test", new ArgsMap(), commands::add, null);
    LowMemoryHandler handler = new LowMemoryHandler("onLowMemory", pool, 1, null, true);
    runtime.init(CountingProbe.class, null, null, null, null, new LowMemoryHandler[] {handler});
    try {
      onWorkerThread(runtime::start);
      MessageCommand warning =
          (MessageCommand)
              awaitCommand(
                  commands,
                  command ->
                      command instanceof MessageCommand
                          && ((MessageCommand) command).getMessage().contains("@OnLowMemory"));
      return warning.getMessage();
    } finally {
      runtime.handleExit(0);
    }
  }

  @Test
  void handlerInvocationFailureIsReportedToTheClient() throws Exception {
    List<Command> commands = Collections.synchronizedList(new ArrayList<Command>());
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8("low-memory-failure-test", new ArgsMap(), commands::add, null);
    LowMemoryHandler handler = new LowMemoryHandler("onLowMemory", "Metaspace", 1, null, true);
    runtime.init(
        WrongSignatureProbe.class, null, null, null, null, new LowMemoryHandler[] {handler});
    try {
      onWorkerThread(() -> runtime.runLowMemoryHandler(handler, USAGE));

      ErrorCommand error =
          (ErrorCommand) awaitCommand(commands, command -> command instanceof ErrorCommand);
      assertTrue(
          error.getCause() instanceof NoSuchMethodException,
          "the client was not told why the handler could not run: " + error.getCause());
    } finally {
      runtime.handleExit(0);
    }
  }

  /**
   * Runs the action on a fresh thread, as the runtime runs handlers on its own worker thread. The
   * test thread may still be entered into a runtime another test left behind.
   */
  static void onWorkerThread(Runnable action) throws InterruptedException {
    Thread worker = new Thread(action, "BTrace Worker (test)");
    worker.setDaemon(true);
    worker.start();
    worker.join(TimeUnit.SECONDS.toMillis(5));
    assertFalse(worker.isAlive(), "the handler did not return");
  }

  static Command awaitCommand(List<Command> commands, Predicate<Command> match)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      synchronized (commands) {
        for (Command command : commands) {
          if (match.test(command)) {
            return command;
          }
        }
      }
      Thread.sleep(10);
    }
    synchronized (commands) {
      throw new AssertionError("no matching command reached the client; got " + commands);
    }
  }
}
