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

import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.handlers.ExitHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BTraceRuntimeImplBaseTest {
  @Test
  void speculativeOperationsDoNotMutateAfterClearLinearizes() throws Exception {
    assertClearInterleaving(
        "send", (manager, queue) -> manager.send(new MessageCommand("send"), queue));
    assertClearInterleaving(
        "speculation", (manager, queue) -> assertTrue(manager.speculation() >= 0));

    BTraceRuntimeImplBase.SpeculativeQueueManager speculateManager =
        new BTraceRuntimeImplBase.SpeculativeQueueManager();
    int speculateId = speculateManager.speculation();
    assertClearInterleaving(
        speculateManager, "speculate", (manager, queue) -> manager.speculate(speculateId));

    BTraceRuntimeImplBase.SpeculativeQueueManager commitManager =
        new BTraceRuntimeImplBase.SpeculativeQueueManager();
    int commitId = commitManager.speculation();
    commitManager.speculate(commitId);
    commitManager.send(new MessageCommand("buffered"), new CommandQueue(16));
    assertClearInterleaving(
        commitManager, "commit", (manager, queue) -> manager.commit(commitId, queue));

    BTraceRuntimeImplBase.SpeculativeQueueManager discardManager =
        new BTraceRuntimeImplBase.SpeculativeQueueManager();
    int discardId = discardManager.speculation();
    discardManager.speculate(discardId);
    assertClearInterleaving(
        discardManager, "discard", (manager, queue) -> manager.discard(discardId));
  }

  @Test
  void terminalMarkerPrecedesOneFirstCodeExitAndClosesSpeculation() throws Exception {
    List<Command> commands = Collections.synchronizedList(new ArrayList<Command>());
    CountDownLatch bufferedMessage = new CountDownLatch(1);
    CountDownLatch postExitMessage = new CountDownLatch(1);
    CountDownLatch terminalExit = new CountDownLatch(1);
    CountDownLatch exitDispatchStarted = new CountDownLatch(1);
    CountDownLatch allowExitDispatch = new CountDownLatch(1);
    CountDownLatch shutdownReturned = new CountDownLatch(1);
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8(
            "issue-888-runtime-test",
            new ArgsMap(),
            command -> {
              commands.add(command);
              if (command instanceof MessageCommand
                  && "buffered".equals(((MessageCommand) command).getMessage())) {
                bufferedMessage.countDown();
              }
              if (command instanceof MessageCommand
                  && "post-exit".equals(((MessageCommand) command).getMessage())) {
                postExitMessage.countDown();
              }
              if (command instanceof ExitCommand) {
                exitDispatchStarted.countDown();
                try {
                  allowExitDispatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
                terminalExit.countDown();
              }
            },
            null);

    int speculation = runtime.speculation();
    assertTrue(speculation >= 0);
    runtime.speculate(speculation);
    runtime.send(new MessageCommand("buffered"));
    runtime.commit(speculation);
    assertTrue(bufferedMessage.await(5, TimeUnit.SECONDS));

    Thread shutdown =
        new Thread(
            () -> {
              runtime.handleExit(23);
              shutdownReturned.countDown();
            },
            "issue-888-terminal-shutdown");
    shutdown.start();
    assertTrue(exitDispatchStarted.await(5, TimeUnit.SECONDS));
    assertFalse(shutdownReturned.await(250, TimeUnit.MILLISECONDS));
    allowExitDispatch.countDown();
    assertTrue(shutdownReturned.await(5, TimeUnit.SECONDS));
    runtime.handleExit(99);
    assertTrue(terminalExit.await(5, TimeUnit.SECONDS));

    int marker = -1;
    int exit = -1;
    int exits = 0;
    synchronized (commands) {
      for (int index = 0; index < commands.size(); index++) {
        Command command = commands.get(index);
        if (command instanceof MessageCommand
            && ((MessageCommand) command).getMessage().startsWith("[BTRACE] terminal cleanup:")) {
          marker = index;
        }
        if (command instanceof ExitCommand) {
          exits++;
          exit = index;
          assertEquals(23, ((ExitCommand) command).getExitCode());
        }
      }
    }
    assertTrue(marker >= 0);
    assertTrue(marker < exit);
    assertEquals(1, exits);

    assertEquals(-1, runtime.speculation());
    runtime.send(new MessageCommand("post-exit"));
    assertFalse(postExitMessage.await(250, TimeUnit.MILLISECONDS));
  }

  /** Probe stand-in whose {@code @OnExit} handler blocks until the test releases it. */
  public static final class BlockingExitProbe {
    static volatile CountDownLatch entered;
    static volatile CountDownLatch release;
    static volatile int exitCode = -1;

    public static void onExit(int code) throws InterruptedException {
      exitCode = code;
      entered.countDown();
      release.await(10, TimeUnit.SECONDS);
    }
  }

  /**
   * Regression for #932: {@code @OnExit} handlers are user code and must not run under the runtime
   * monitor, or every other user of that monitor stalls behind whatever the handler waits on. They
   * must still complete before the terminal marker is dispatched (#908 ordering).
   */
  @Test
  void exitHandlersRunOutsideTheRuntimeMonitorAndBeforeTheTerminalMarker() throws Exception {
    BlockingExitProbe.entered = new CountDownLatch(1);
    BlockingExitProbe.release = new CountDownLatch(1);
    BlockingExitProbe.exitCode = -1;
    List<Command> commands = Collections.synchronizedList(new ArrayList<Command>());
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8("issue-932-exit-handler-test", new ArgsMap(), commands::add, null);
    runtime.init(
        BlockingExitProbe.class,
        null,
        null,
        null,
        new ExitHandler[] {new ExitHandler("onExit")},
        null);

    Thread shutdown = new Thread(() -> runtime.handleExit(17), "issue-932-terminal-shutdown");
    shutdown.setDaemon(true);
    CountDownLatch monitorAcquired = new CountDownLatch(1);
    Thread contender =
        new Thread(
            () -> {
              synchronized (runtime) {
                monitorAcquired.countDown();
              }
            },
            "issue-932-runtime-monitor-user");
    contender.setDaemon(true);
    try {
      shutdown.start();
      assertTrue(BlockingExitProbe.entered.await(5, TimeUnit.SECONDS), "@OnExit never ran");
      contender.start();
      assertTrue(
          monitorAcquired.await(5, TimeUnit.SECONDS),
          "the runtime monitor was held across the @OnExit handler");
      assertFalse(
          containsTerminalMarker(commands),
          "terminal marker dispatched before the @OnExit handler completed");
    } finally {
      BlockingExitProbe.release.countDown();
    }
    shutdown.join(TimeUnit.SECONDS.toMillis(10));
    assertFalse(shutdown.isAlive(), "terminal shutdown did not complete");
    assertEquals(17, BlockingExitProbe.exitCode);
    assertTrue(containsTerminalMarker(commands), "terminal marker was never dispatched");
  }

  private static boolean containsTerminalMarker(List<Command> commands) {
    synchronized (commands) {
      for (Command command : commands) {
        if (command instanceof MessageCommand
            && ((MessageCommand) command).getMessage().startsWith("[BTRACE] terminal cleanup:")) {
          return true;
        }
      }
    }
    return false;
  }

  private static void assertClearInterleaving(String operation, SpeculativeOperation action)
      throws Exception {
    assertClearInterleaving(new BTraceRuntimeImplBase.SpeculativeQueueManager(), operation, action);
  }

  private static void assertClearInterleaving(
      BTraceRuntimeImplBase.SpeculativeQueueManager manager,
      String operation,
      SpeculativeOperation action)
      throws Exception {
    CommandQueue queue = new CommandQueue(16);
    CountDownLatch mutationPaused = new CountDownLatch(1);
    CountDownLatch releaseMutation = new CountDownLatch(1);
    CountDownLatch clearFinished = new CountDownLatch(1);
    manager.setTestHook(
        op -> {
          if (operation.equals(op)) {
            mutationPaused.countDown();
            try {
              releaseMutation.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          }
        });

    Thread mutation = new Thread(() -> action.apply(manager, queue), "issue-888-" + operation);
    mutation.start();
    assertTrue(mutationPaused.await(5, TimeUnit.SECONDS), operation + " did not reach its hook");
    Thread clear =
        new Thread(
            () -> {
              manager.clear(queue);
              clearFinished.countDown();
            },
            "issue-888-clear-" + operation);
    clear.start();
    assertFalse(clearFinished.await(250, TimeUnit.MILLISECONDS), "clear passed a read-side owner");
    releaseMutation.countDown();
    mutation.join(5000L);
    assertFalse(mutation.isAlive());
    assertTrue(clearFinished.await(5, TimeUnit.SECONDS));
    clear.join(5000L);

    assertEquals(0, manager.speculativeQueueCountForTest());
    assertEquals(0, queue.sizeForTest());
    assertEquals(-1, manager.speculation());
    manager.send(new MessageCommand("after-clear"), queue);
    manager.speculate(0);
    manager.commit(0, queue);
    manager.discard(0);
    assertEquals(0, manager.speculativeQueueCountForTest());
    assertEquals(0, queue.sizeForTest());
  }

  private interface SpeculativeOperation {
    void apply(BTraceRuntimeImplBase.SpeculativeQueueManager manager, CommandQueue queue);
  }
}
