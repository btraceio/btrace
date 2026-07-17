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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.client.Client;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.StatusCommand;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tests.harness.Completion;

/** Real staged-JAR client/agent/target lifecycle coverage for #888. */
public class Issue888RuntimeHardeningIntegrationTest extends RuntimeTest {
  private static final int EXIT_CODE = 23;
  private static final String TERMINAL_MARKER = "[BTRACE] terminal cleanup:";

  @BeforeAll
  static void setup() {
    classSetup();
  }

  @AfterEach
  @Override
  public void reset() {
    super.reset();
  }

  @Test
  void isolatesThrowingHandlersAndCompletesTerminalMBeanCleanup() throws Exception {
    System.setProperty("btrace.comm.protocol", "2");
    System.setProperty("btrace.comm.autoNegotiate", "false");
    System.setProperty("btrace.comm.forceVersion", "true");
    ensureBTracePort();

    Process target = startTarget();
    LinkedBlockingQueue<String> targetOutput = new LinkedBlockingQueue<>();
    LinkedBlockingQueue<String> targetErrors = new LinkedBlockingQueue<>();
    List<String> targetOutputHistory = Collections.synchronizedList(new ArrayList<String>());
    List<String> targetErrorHistory = Collections.synchronizedList(new ArrayList<String>());
    Thread targetPump =
        pump(target.getInputStream(), targetOutput, targetOutputHistory, "issue-888-target-out");
    Thread errorPump =
        pump(target.getErrorStream(), targetErrors, targetErrorHistory, "issue-888-target-err");
    String ready = awaitLine(targetOutput, "ready:", 10L);
    assertNotNull(ready, "target did not report its PID");
    String pid = ready.substring("ready:".length());
    Client client = null;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (PrintWriter targetInput = new PrintWriter(target.getOutputStream(), true)) {
      try {
        File trace = locateTrace("btrace/Issue888RuntimeHardeningTest.java");
        client = createClientForTests(trace.getParentFile().getAbsolutePath());
        client.attach(pid, null, getEventsClassPath());
        byte[] code = readProbeClass();
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<Integer> terminalExit = new CompletableFuture<>();
        List<Command> commands = Collections.synchronizedList(new ArrayList<Command>());
        LinkedBlockingQueue<String> probeMessages = new LinkedBlockingQueue<>();
        Client activeClient = client;
        Future<?> submission =
            executor.submit(
                () -> {
                  try {
                    activeClient.submit(
                        "localhost",
                        trace.getName(),
                        code,
                        new String[0],
                        command ->
                            handleCommand(command, started, terminalExit, commands, probeMessages));
                  } catch (Throwable failure) {
                    started.completeExceptionally(failure);
                    terminalExit.completeExceptionally(failure);
                  }
                });

        started.get(20, TimeUnit.SECONDS);
        assertTrue(awaitMBeanStatus(targetInput, targetOutput, "mbean:present:token=true"));
        activeClient.sendEvent("issue-888");
        targetInput.println("work");
        assertTrue(awaitMessage(probeMessages, "issue-888-event-handler"));
        assertTrue(awaitMessage(probeMessages, "issue-888-method-handler"));
        assertTrue(awaitMessage(probeMessages, "issue-888-error-handler"));
        assertTrue(awaitMessage(probeMessages, "issue-888-normal-after-failure"));
        assertTrue(
            awaitHandlerDiagnostics(targetErrors, targetErrorHistory),
            "the target did not retain both handler diagnostics on stderr; stdout="
                + targetOutputHistory
                + ", stderr="
                + targetErrorHistory);

        // A real inbound Exit is the terminal trigger. The client command loop must still receive
        // the runtime-owned marker and generated Exit after submitting it.
        activeClient.sendExit(EXIT_CODE);
        assertEquals(EXIT_CODE, terminalExit.get(20, TimeUnit.SECONDS).intValue());
        submission.get(20, TimeUnit.SECONDS);
        assertTerminalOrder(commands);
        assertTrue(awaitMBeanStatus(targetInput, targetOutput, "mbean:absent"));
      } finally {
        if (client != null) {
          client.close();
        }
        executor.shutdownNow();
        targetInput.println("done");
        if (!target.waitFor(10, TimeUnit.SECONDS)) {
          target.destroyForcibly();
        }
        targetPump.join(1000L);
        errorPump.join(1000L);
      }
    }
  }

  @Test
  void targetShutdownDeliversSuccessfulExitToCli() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnTimerArgTest.java",
        new String[] {"timer=200"},
        Completion.untilContains("timer"),
        (stdout, stderr, exitCode, jfrFile) -> {
          assertEquals(0, exitCode, "target shutdown must deliver a successful terminal exit");
          assertTrue(stderr.isEmpty(), "unexpected BTrace client stderr: " + stderr);
          assertTrue(stdout.contains("timer"), "expected timer output");
        });
  }

  private Process startTarget() throws Exception {
    List<String> args = new ArrayList<>();
    args.add(Paths.get(javaHome, "bin", "java").toString());
    args.add("-cp");
    args.add(getTargetAppClassPath());
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    args.add("-XX:+EnableDynamicAgentLoading");
    args.add("-XX:+UnlockDiagnosticVMOptions");
    args.add("-XX:-OmitStackTraceInFastThrow");
    args.add("-Dbtrace.test");
    args.add("-Dio.btrace.libs.org.slf4j.simpleLogger.logFile=System.err");
    args.add("-Dio.btrace.libs.org.slf4j.simpleLogger.log.io.btrace=trace");
    args.add("resources.Issue888RuntimeHardeningTarget");
    ProcessBuilder builder = new ProcessBuilder(args);
    // The child is an uninstrumented target; inherited test-only options must not alter agent
    // discovery or the target's own networking configuration.
    builder.environment().remove("JAVA_TOOL_OPTIONS");
    return builder.start();
  }

  private static byte[] readProbeClass() throws Exception {
    Path probe =
        Paths.get(
            System.getProperty("project.dir"),
            "build/classes/btrace/Issue888RuntimeHardeningTest.class");
    assertTrue(Files.isRegularFile(probe), "Precompiled BTrace probe is missing: " + probe);
    return Files.readAllBytes(probe);
  }

  private static void handleCommand(
      Command command,
      CompletableFuture<Void> started,
      CompletableFuture<Integer> terminalExit,
      List<Command> commands,
      LinkedBlockingQueue<String> probeMessages) {
    commands.add(command);
    if (command instanceof StatusCommand) {
      StatusCommand status = (StatusCommand) command;
      if (status.getFlag() == StatusCommand.STATUS_FLAG && status.isSuccess()) {
        started.complete(null);
      } else if (!status.isSuccess()) {
        started.completeExceptionally(new IllegalStateException("Probe startup failed"));
      }
    } else if (command instanceof MessageCommand) {
      offerOrFail(probeMessages, ((MessageCommand) command).getMessage(), "probe message");
    } else if (command instanceof ExitCommand) {
      terminalExit.complete(((ExitCommand) command).getExitCode());
    }
  }

  private static void assertTerminalOrder(List<Command> commands) {
    int marker = -1;
    int exits = 0;
    int exitIndex = -1;
    synchronized (commands) {
      for (int index = 0; index < commands.size(); index++) {
        Command command = commands.get(index);
        if (command instanceof MessageCommand
            && ((MessageCommand) command).getMessage().startsWith(TERMINAL_MARKER)) {
          marker = index;
        }
        if (command instanceof ExitCommand) {
          exits++;
          exitIndex = index;
          assertEquals(EXIT_CODE, ((ExitCommand) command).getExitCode());
        }
      }
    }
    assertTrue(marker >= 0, "missing terminal cleanup marker");
    assertEquals(1, exits, "terminal protocol emitted more than one ExitCommand");
    assertTrue(marker < exitIndex, "terminal marker must precede the generated ExitCommand");
  }

  private static boolean awaitMBeanStatus(
      PrintWriter input, LinkedBlockingQueue<String> output, String expected) throws Exception {
    input.println("mbean-status");
    return awaitContaining(output, null, expected, 10L);
  }

  private static String awaitLine(LinkedBlockingQueue<String> output, String prefix, long timeout)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      String line = output.poll(100L, TimeUnit.MILLISECONDS);
      if (line != null && line.startsWith(prefix)) {
        return line;
      }
    }
    return null;
  }

  private static boolean awaitMessage(LinkedBlockingQueue<String> messages, String expected)
      throws InterruptedException {
    return awaitContaining(messages, null, expected, 10L);
  }

  private static boolean awaitHandlerDiagnostics(
      LinkedBlockingQueue<String> errors, List<String> errorHistory) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
    while (System.nanoTime() < deadline) {
      errors.poll(100L, TimeUnit.MILLISECONDS);
      synchronized (errorHistory) {
        String diagnostics = errorHistory.toString();
        if (diagnostics.contains("ERROR")
            && diagnostics.contains("BTrace handler execution failed")
            && diagnostics.contains("BTrace error-handler execution failed")
            && diagnostics.contains("StringIndexOutOfBoundsException")) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean awaitContaining(
      LinkedBlockingQueue<String> first,
      LinkedBlockingQueue<String> second,
      String expected,
      long timeoutSeconds)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (System.nanoTime() < deadline) {
      String firstLine = first.poll(100L, TimeUnit.MILLISECONDS);
      if (firstLine != null && firstLine.contains(expected)) {
        return true;
      }
      if (second != null) {
        String secondLine = second.poll();
        if (secondLine != null && secondLine.contains(expected)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Thread pump(
      final InputStream stream,
      final LinkedBlockingQueue<String> lines,
      final List<String> history,
      String name) {
    Thread pump =
        new Thread(
            () -> {
              try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                  offerOrFail(lines, line, name + " output");
                  if (history != null) {
                    history.add(line);
                  }
                }
              } catch (IOException ignored) {
                // The target is deliberately closed in test cleanup.
              }
            },
            name);
    pump.setDaemon(true);
    pump.start();
    return pump;
  }

  private static void offerOrFail(LinkedBlockingQueue<String> lines, String line, String source) {
    if (!lines.offer(line)) {
      throw new IllegalStateException("Unable to retain " + source);
    }
  }
}
