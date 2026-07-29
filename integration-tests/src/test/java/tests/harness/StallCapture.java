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
package tests.harness;

import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes a diagnostic snapshot of the test JVM and every live target JVM.
 *
 * <p>This runs only when a test has already stopped making progress, so it is written to survive a
 * hostile environment: nothing it does can throw, every wait is bounded, and each section is
 * flushed as it is produced so that a section which hangs still leaves the earlier ones on disk.
 *
 * <p>It also must not perturb the system under test. In particular it does not send {@code SIGQUIT}
 * to a target: HotSpot writes that dump to the target's stdout, and for {@code testStartup} targets
 * that stream is accumulated by {@link OutputPump} into the buffer the test validators assert on. A
 * diagnostic that fails the test it is diagnosing is worse than no diagnostic.
 */
public final class StallCapture {
  /** Prefix on every line, so a capture interleaved with the target reader threads greps out. */
  static final String PREFIX = "[stall-dump] ";

  private static final long JCMD_TIMEOUT_MS = 10_000L;
  private static final long THREAD_DUMP_TIMEOUT_MS = 15_000L;

  private StallCapture() {}

  /**
   * Writes one capture frame.
   *
   * @param sink where the report goes; flushed after each section if it is {@link Flushable}
   * @param label identifies the stalled test
   * @param elapsedMs how long the test had been running when the watchdog fired
   * @param frame 1-based frame number; frames are compared to tell "wedged" from "slow"
   * @param targets the targets to dump, snapshotted when the watchdog fired
   * @param budgetMs an overall budget for all targets together, so accumulated targets cannot
   *     stretch a capture without bound
   */
  public static void capture(
      Appendable sink,
      String label,
      long elapsedMs,
      int frame,
      List<TargetRegistry.Snapshot> targets,
      long budgetMs) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);

    writeBanner(sink, label, elapsedMs, frame, targets.size());
    flush(sink);

    writeTestJvmThreads(sink);
    // Charged against the same budget as the targets below: the join above can take up to
    // THREAD_DUMP_TIMEOUT_MS, and a frame that overruns its budget delays the next one.
    flush(sink);

    for (TargetRegistry.Snapshot target : targets) {
      // The header goes out for every target, dumped or not, so one grep pattern finds them all.
      line(sink, "---- target " + target.getLabel() + " (pid " + target.getPid() + ") ----");
      if (System.nanoTime() >= deadline) {
        line(sink, "skipped, capture budget exhausted");
      } else {
        writeTargetThreads(sink, target);
      }
      flush(sink);
    }

    line(sink, "==== end of stall dump frame " + frame + " ====");
    flush(sink);
  }

  private static void writeBanner(
      Appendable sink, String label, long elapsedMs, int frame, int targetCount) {
    line(sink, "");
    line(sink, "======================================================================");
    line(sink, "STALL DUMP frame " + frame + " -- test made no progress");
    line(sink, "test:     " + label);
    line(sink, "elapsed:  " + (elapsedMs / 1000L) + "s");
    line(sink, "targets:  " + targetCount);
    line(sink, "======================================================================");
  }

  /**
   * Dumps this JVM's threads.
   *
   * <p>Done on a throwaway thread with a bounded join: {@code dumpAllThreads} needs a safepoint,
   * and a JVM that cannot reach one is exactly the sort of thing being diagnosed. Losing this
   * section to a timeout is survivable; losing the whole capture to it is not.
   */
  private static void writeTestJvmThreads(Appendable sink) {
    line(sink, "---- test JVM threads ----");
    final AtomicReference<String> result = new AtomicReference<>();
    Thread dumper =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  result.set(renderThreadDump());
                } catch (Throwable t) {
                  result.set("test-JVM dump failed: " + t);
                }
              }
            },
            "btrace-stall-thread-dump");
    dumper.setDaemon(true);
    dumper.start();
    try {
      dumper.join(THREAD_DUMP_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    String dump = result.get();
    line(
        sink,
        dump != null ? dump : "test-JVM dump timed out after " + THREAD_DUMP_TIMEOUT_MS + "ms");
  }

  /**
   * Renders a full dump.
   *
   * <p>Rendered frame by frame rather than with {@link ThreadInfo#toString()}, which stops after
   * eight frames -- and the frame that explains a stall is rarely in the top eight of a
   * test-harness stack.
   */
  private static String renderThreadDump() {
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    ThreadInfo[] infos = threads.dumpAllThreads(true, true);
    StringBuilder sb = new StringBuilder();
    for (ThreadInfo info : infos) {
      if (info == null) {
        continue;
      }
      sb.append('"').append(info.getThreadName()).append('"');
      sb.append(" Id=").append(info.getThreadId());
      sb.append(' ').append(info.getThreadState());
      if (info.getLockName() != null) {
        sb.append(" on ").append(info.getLockName());
      }
      if (info.getLockOwnerName() != null) {
        sb.append(" owned by \"").append(info.getLockOwnerName());
        sb.append("\" Id=").append(info.getLockOwnerId());
      }
      if (info.isSuspended()) {
        sb.append(" (suspended)");
      }
      if (info.isInNative()) {
        sb.append(" (in native)");
      }
      sb.append('\n');
      StackTraceElement[] stack = info.getStackTrace();
      for (int i = 0; i < stack.length; i++) {
        sb.append("\tat ").append(stack[i]).append('\n');
        for (MonitorInfo monitor : info.getLockedMonitors()) {
          if (monitor.getLockedStackDepth() == i) {
            sb.append("\t-  locked ").append(monitor).append('\n');
          }
        }
      }
      LockInfo[] synchronizers = info.getLockedSynchronizers();
      if (synchronizers.length > 0) {
        sb.append("\tNumber of locked synchronizers = ").append(synchronizers.length).append('\n');
        for (LockInfo synchronizer : synchronizers) {
          sb.append("\t-  ").append(synchronizer).append('\n');
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * Dumps one target's threads with {@code jcmd}.
   *
   * <p>Output is redirected to a file rather than left on a pipe: {@code Thread.print -l} on an
   * instrumented target easily exceeds the 64 KB pipe buffer, and a jcmd blocked on a full pipe
   * would be killed by the timeout below having produced nothing.
   */
  private static void writeTargetThreads(Appendable sink, TargetRegistry.Snapshot target) {
    String pid = target.getPid();
    if (pid == null) {
      line(sink, "no pid known for this target; it never reported one");
      return;
    }
    File output = null;
    Process jcmd = null;
    try {
      output = File.createTempFile("btrace-stall-", ".txt");
      ProcessBuilder pb =
          new ProcessBuilder(target.getJcmdPath(), pid, "Thread.print", "-l")
              .redirectErrorStream(true)
              .redirectOutput(output);
      jcmd = pb.start();
      if (!jcmd.waitFor(JCMD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        jcmd.destroyForcibly();
        line(sink, "jcmd did not complete within " + JCMD_TIMEOUT_MS + "ms (attach jammed?)");
      }
      byte[] captured = Files.readAllBytes(output.toPath());
      if (captured.length == 0) {
        line(sink, "jcmd produced no output");
      } else {
        line(sink, new String(captured, StandardCharsets.UTF_8));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      line(sink, "interrupted while waiting for jcmd");
    } catch (Throwable t) {
      line(sink, "jcmd failed: " + t);
    } finally {
      if (jcmd != null && jcmd.isAlive()) {
        jcmd.destroyForcibly();
      }
      if (output != null && !output.delete()) {
        output.deleteOnExit();
      }
    }
  }

  /** Appends {@code text} with every line prefixed. Never throws. */
  static void line(Appendable sink, String text) {
    try {
      for (String part : text.split("\n", -1)) {
        sink.append(PREFIX).append(part).append('\n');
      }
    } catch (Throwable ignored) {
      // A capture that cannot write is still better than a capture that throws out of a
      // scheduler thread.
    }
  }

  private static void flush(Appendable sink) {
    if (sink instanceof Flushable) {
      try {
        ((Flushable) sink).flush();
      } catch (IOException ignored) {
        // Best effort.
      }
    }
  }
}
