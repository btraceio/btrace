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

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.handlers.LowMemoryHandler;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** {@code @OnLowMemory} handling in the runtime, driven without real memory pressure. */
class BTraceRuntimeLowMemoryTest {
  private static final MemoryUsage USAGE = new MemoryUsage(0, 2048, 4096, 8192);

  /** Probe stand-in whose handler takes a parameter the runtime cannot supply. */
  public static final class WrongSignatureProbe {
    public static void onLowMemory(String notAMemoryUsage) {}
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
      runtime.runLowMemoryHandler(handler, USAGE);

      ErrorCommand error =
          (ErrorCommand) awaitCommand(commands, command -> command instanceof ErrorCommand);
      assertTrue(
          error.getCause() instanceof NoSuchMethodException,
          "the client was not told why the handler could not run: " + error.getCause());
    } finally {
      runtime.handleExit(0);
    }
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
