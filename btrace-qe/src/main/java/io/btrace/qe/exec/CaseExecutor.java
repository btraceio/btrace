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
package io.btrace.qe.exec;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.GivenDef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CaseExecutor {
  private final QeEnvironment env;

  public CaseExecutor(QeEnvironment env) {
    this.env = env;
  }

  /** Mutable per-case state shared with step bodies running on the watchdog thread. */
  private static final class State {
    volatile TargetProcess process;

    /** The CLI attached by an {@code attach} step; null in other modes. */
    volatile TargetProcess client;

    volatile Boolean canaryOk;
    volatile boolean shutdownSent;
    volatile boolean diedBeforeReady;
  }

  /** How long {@code detach} gives the client to run its shutdown hook before it is killed. */
  private static final long CLIENT_DETACH_GRACE_MS = 5_000L;

  public ExecutionTrace execute(final CaseDef def, final Path runDir) {
    final long stepTimeoutMs = env.defaultTimeoutMs();
    final State state = new State();
    List<StepOutcome> outcomes = new ArrayList<>();
    boolean timedOut = false;
    String threadDump = null;

    ExecutorService watchdog =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "qe-step");
              t.setDaemon(true);
              return t;
            });
    try {
      for (final String step : def.steps()) {
        long start = System.currentTimeMillis();
        Future<?> future =
            watchdog.submit(
                () -> {
                  runStep(step, def, runDir, stepTimeoutMs, state);
                  return null;
                });
        try {
          future.get(stepTimeoutMs, TimeUnit.MILLISECONDS);
          outcomes.add(StepOutcome.ok(step, System.currentTimeMillis() - start));
        } catch (TimeoutException te) {
          future.cancel(true);
          timedOut = true;
          TargetProcess p = state.process;
          threadDump = p != null && p.pid() > 0 && p.isAlive() ? captureThreadDump(p.pid()) : null;
          outcomes.add(StepOutcome.failed(step, System.currentTimeMillis() - start, "timed out"));
          break;
        } catch (Exception e) {
          Throwable cause = e.getCause() != null ? e.getCause() : e;
          outcomes.add(
              StepOutcome.failed(step, System.currentTimeMillis() - start, String.valueOf(cause)));
          break;
        }
        if (state.diedBeforeReady) {
          break;
        }
      }
    } finally {
      watchdog.shutdownNow();
      try {
        // a step still inside launch() may publish its process late; wait so we can reap it
        watchdog.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }

    // the client goes first: a client still attached would otherwise see the target vanish
    TargetProcess client = state.client;
    if (client != null) {
      reap(client);
    }
    TargetProcess p = state.process;
    Integer exitCode = null;
    boolean exitedPrematurely = false;
    if (p != null) {
      if (!p.isAlive()) {
        exitCode = p.process().exitValue();
        exitedPrematurely = def.given().mode() != GivenDef.Mode.CLIENT && !state.shutdownSent;
      }
      reap(p);
    }
    List<String> stdout = p != null ? p.stdoutSnapshot() : Collections.<String>emptyList();
    List<String> stderr = p != null ? p.stderrSnapshot() : Collections.<String>emptyList();
    return new ExecutionTrace(
        outcomes,
        timedOut,
        exitedPrematurely,
        exitCode,
        stdout,
        stderr,
        state.canaryOk,
        threadDump,
        hasHsErrLog(runDir),
        runDir,
        client != null ? client.stdoutSnapshot() : Collections.<String>emptyList(),
        client != null ? client.stderrSnapshot() : Collections.<String>emptyList());
  }

  /** Kills a process that is still alive and drains its output so the snapshots are complete. */
  private static void reap(TargetProcess p) {
    if (p.isAlive()) {
      p.destroyForcibly();
      try {
        p.process().waitFor(5, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      p.awaitOutputDrained(2_000L);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void runStep(String step, CaseDef def, Path runDir, long timeoutMs, State state)
      throws Exception {
    switch (step) {
      case "launch":
        state.process = new TargetLauncher().launch(def.given(), env, runDir);
        break;
      case "awaitReady":
        {
          String ready = state.process.awaitLine("ready:", timeoutMs);
          if (ready == null) {
            if (!state.process.isAlive()) {
              state.diedBeforeReady = true;
              return;
            }
            // still alive but silent: block so the watchdog records a timeout with a thread dump
            Thread.sleep(Long.MAX_VALUE);
          }
          state.process.assignPid(Long.parseLong(ready.substring("ready:".length()).trim()));
          break;
        }
      case "canary":
        // leave headroom inside the step budget for the canary's detach grace period
        state.canaryOk =
            new ClientProbe().runCanary(state.process, env, Math.max(1_000L, timeoutMs - 6_000L));
        break;
      case "shutdown":
        if (state.process.isAlive()) {
          state.process.writeStdin("exit");
          state.shutdownSent = true;
        }
        break;
      case "runClient":
        state.process = new ClientLauncher().launch(def.given().clientArgs(), env, runDir);
        break;
      case "attach":
        state.client =
            new ClientLauncher()
                .launch(
                    resolveClientArgs(def.given().clientArgs(), state.process, runDir),
                    env,
                    runDir);
        break;
      case "awaitOutput":
        // bounded inside the step budget: a missing line is a finding for the oracle, not a hang
        awaitOutput(
            state.client, def.expect().outputMustMatch(), Math.max(1_000L, timeoutMs - 3_000L));
        break;
      case "detach":
        detach(state.client);
        break;
      case "awaitExit":
        if (!state.process.process().waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
          Thread.sleep(Long.MAX_VALUE);
        }
        break;
      default:
        throw new IllegalStateException("unknown step: " + step);
    }
  }

  /**
   * Substitutes {@code ${pid}} (the launched target's PID), {@code ${port}} (the port the harness
   * reserved for the target) and {@code ${runDir}} in the client arguments.
   */
  static List<String> resolveClientArgs(List<String> raw, TargetProcess target, Path runDir) {
    List<String> out = new ArrayList<>(raw.size());
    for (String arg : raw) {
      out.add(
          arg.replace("${pid}", String.valueOf(target.pid()))
              .replace("${port}", String.valueOf(target.assignedPort()))
              .replace("${runDir}", runDir.toString()));
    }
    return out;
  }

  /** Waits, in order, for a client stdout line matching each pattern; gives up at the deadline. */
  private static void awaitOutput(TargetProcess client, List<String> patterns, long budgetMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + budgetMs;
    int from = 0;
    for (String regex : patterns) {
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        return;
      }
      int at = client.awaitMatch(Pattern.compile(regex), from, remaining);
      if (at < 0) {
        return;
      }
      from = at + 1;
    }
  }

  /**
   * Ends the client session the way Ctrl+C would: a TERM signal runs the client's shutdown hook,
   * which sends the exit command to the agent; a client that ignores it is killed.
   */
  private static void detach(TargetProcess client) throws InterruptedException {
    if (client == null || !client.isAlive()) {
      return;
    }
    client.process().destroy();
    if (!client.process().waitFor(CLIENT_DETACH_GRACE_MS, TimeUnit.MILLISECONDS)) {
      client.destroyForcibly();
      client.process().waitFor(CLIENT_DETACH_GRACE_MS, TimeUnit.MILLISECONDS);
    }
  }

  private String captureThreadDump(long pid) {
    Path out = null;
    try {
      out = Files.createTempFile("qe-jcmd-", ".txt");
      Process p =
          new ProcessBuilder(env.jcmdPath(), String.valueOf(pid), "Thread.print")
              .redirectErrorStream(true)
              .redirectOutput(out.toFile())
              .start();
      boolean finished = p.waitFor(10, TimeUnit.SECONDS);
      if (!finished) {
        p.destroyForcibly();
        p.waitFor(2, TimeUnit.SECONDS);
      }
      String text = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
      return finished ? text : text + "\n[jcmd timed out after 10s]";
    } catch (Exception e) {
      return "failed to capture thread dump: " + e;
    } finally {
      if (out != null) {
        try {
          Files.deleteIfExists(out);
        } catch (IOException ignored) {
          // best effort
        }
      }
    }
  }

  private static boolean hasHsErrLog(Path runDir) {
    if (!Files.isDirectory(runDir)) {
      return false;
    }
    try (Stream<Path> files = Files.list(runDir)) {
      return files.anyMatch(f -> f.getFileName().toString().startsWith("hs_err_pid"));
    } catch (IOException e) {
      return false;
    }
  }
}
