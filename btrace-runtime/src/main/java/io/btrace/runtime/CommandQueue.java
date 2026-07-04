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

import io.btrace.core.comm.Command;
import io.btrace.core.comm.MessageCommand;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscChunkedArrayQueue;

final class CommandQueue {
  private static final long DROP_TIMEOUT_MS =
      Long.getLong("io.btrace.runtime.cmd.dropTimeoutMillis", 1L);
  private static final int BACKOFF_YIELD_ITERS =
      Integer.getInteger("io.btrace.runtime.cmd.backoffYieldIters", 3000);
  private static final int BACKOFF_SLEEP_ITERS =
      Integer.getInteger("io.btrace.runtime.cmd.backoffSleepIters", 100);
  private final MpscChunkedArrayQueue<Command> queue;
  private final AtomicLong droppedCommands = new AtomicLong();

  CommandQueue(int capacity) {
    queue = new MpscChunkedArrayQueue<>(capacity);
  }

  public boolean addAll(Collection<? extends Command> c) {
    boolean rslt = true;
    for (Command cmd : c) {
      if (!enqueue(cmd)) {
        rslt &= false;
      }
    }
    return rslt;
  }

  public void drain(
      MessagePassingQueue.Consumer<Command> c,
      MessagePassingQueue.WaitStrategy wait,
      MessagePassingQueue.ExitCondition exit) {
    queue.drain(
        e -> {
          long dropped = droppedCommands.get();
          if (dropped > 0) {
            c.accept(new MessageCommand("Dropped " + dropped + " commands"));
            droppedCommands.addAndGet(-dropped);
          }
          c.accept(e);
        },
        wait,
        exit);
  }

  public boolean enqueue(Command cmd) {
    int backoffCntr = 0;
    long tsCutOff = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DROP_TIMEOUT_MS);
    // The producer is an arbitrary instrumented application thread - its interrupt
    // status must never be cleared here (Thread.interrupted() would swallow it and
    // corrupt the application's own interruption handling). Always attempt the offer
    // at least once; only refuse to back off when the thread is interrupted, since
    // parkNanos returns immediately for an interrupted thread and the backoff would
    // degenerate into a busy spin.
    while (!queue.relaxedOffer(cmd)) {
      if (Thread.currentThread().isInterrupted() || System.nanoTime() > tsCutOff) {
        droppedCommands.incrementAndGet();
        return false;
      }
      if (backoffCntr < BACKOFF_YIELD_ITERS) {
        Thread.yield();
      } else if (backoffCntr < BACKOFF_YIELD_ITERS + BACKOFF_SLEEP_ITERS) {
        LockSupport.parkNanos(1_000_000);
      }
      backoffCntr++;
    }
    return true;
  }

  public void clear() {
    queue.clear();
    droppedCommands.set(0);
  }
}
