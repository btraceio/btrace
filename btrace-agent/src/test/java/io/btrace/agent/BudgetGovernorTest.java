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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.instr.BTraceProbe;
import io.btrace.instr.HandlerRepositoryImpl;
import io.btrace.instr.ProbeBudgets;
import io.btrace.runtime.IndyDispatcher;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BudgetGovernorTest {
  private static final long DEPLOYED_AT = TimeUnit.SECONDS.toNanos(1000);
  private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);
  // ProbeBudgets state is global; every probe gets a name of its own.
  private static final AtomicInteger PROBE_SEQ = new AtomicInteger();

  private final long[] now = {DEPLOYED_AT};

  @Test
  void noTripBelowCeiling() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(500);
    now[0] += ONE_SECOND;
    governor.tick();

    assertTrue(client.exits.isEmpty());
    assertFalse(ProbeBudgets.isTripped(client.internalName));
  }

  @Test
  void tripsAboveCeiling() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size());
    String reason = client.exits.get(0);
    assertTrue(reason.startsWith("probe " + client.className + " removed: "), reason);
    assertTrue(reason.contains("5000 hits/s"), reason);
    assertTrue(reason.contains("exceeded probeMaxRate=1000"), reason);
    assertTrue(ProbeBudgets.isTripped(client.internalName));
    assertTrue(client.trippedWhenExited, "the probe must be marked tripped before it exits");
  }

  @Test
  void lateTickDoesNotInflateRate() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(2400);
    now[0] += 3 * ONE_SECOND;
    governor.tick();

    assertTrue(client.exits.isEmpty(), "800 hits/s over 3 s is below the ceiling");
  }

  @Test
  void firstTickOnlyBaselines() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    client.hit(1_000_000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertTrue(client.exits.isEmpty());
  }

  @Test
  void ttlTrips() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits("10", null), client);

    now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size());
    String reason = client.exits.get(0);
    assertTrue(
        reason.equals("probe " + client.className + " removed: lifetime exceeded probeTtl=10s"),
        reason);
    assertTrue(ProbeBudgets.isTripped(client.internalName));
  }

  @Test
  void ttlDoesNotTripEarly() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits("10", null), client);

    now[0] = DEPLOYED_AT + 9 * ONE_SECOND;
    governor.tick();

    assertTrue(client.exits.isEmpty());
  }

  @Test
  void exemptClientIsSkipped() throws Exception {
    FakeClient client = new FakeClient();
    client.exempt = true;
    BudgetGovernor governor = governor(limits("10", "1000"), client);

    governor.tick();
    client.hit(1_000_000);
    now[0] += 60 * ONE_SECOND;
    governor.tick();

    assertTrue(client.exits.isEmpty());
    assertFalse(ProbeBudgets.isTripped(client.internalName));
  }

  @Test
  void launchTimeStartupScriptProbeIsSkipped() throws Exception {
    FileClient client =
        new FileClient(new ClientContext(null, null, new ArgsMap(), new SharedSettings()), true);
    String internalName = deployForTest(client);
    try {
      BudgetGovernor governor = governor(limits("10", null), client);

      now[0] = DEPLOYED_AT + 60 * ONE_SECOND;
      governor.tick();

      assertFalse(ProbeBudgets.isTripped(internalName));
      assertFalse(client.isShuttingDown());
    } finally {
      client.onExit(0);
    }
  }

  @Test
  void attachTimeStartupScriptProbeIsRemovedAndItsOutputClosed() throws Exception {
    File output = File.createTempFile("budget-file-client", ".btrace");
    try {
      SharedSettings settings = new SharedSettings();
      settings.setOutputFile(output.getAbsolutePath());
      FileClient client =
          new FileClient(new ClientContext(null, null, new ArgsMap(), settings), false);
      String internalName = deployForTest(client);
      client.setRuntimeForTest(commandThreadRuntime(client));
      BudgetGovernor governor = governor(limits("10", null), client);

      now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
      governor.tick();

      assertTrue(ProbeBudgets.isTripped(internalName), "an attach-time startup probe was skipped");
      assertTrue(client.isShuttingDown(), "the exit did not remove the probe");
      assertFalse(Client.liveClients().contains(client), "the removed probe is still registered");
      String written = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
      String className = internalName.replace('/', '.');
      assertTrue(
          written.contains(
              "BTrace: probe " + className + " removed: lifetime exceeded probeTtl=10s"),
          "the reason was not written to the probe's output file:\n" + written);
      client.out.print("after removal");
      assertTrue(client.out.checkError(), "the probe's output file was left open");

      // The exit finished the removal, so nothing is forced afterwards.
      now[0] += 10 * ONE_SECOND;
      governor.tick();
    } finally {
      assertTrue(output.delete() || !output.exists());
    }
  }

  @Test
  void removingAStartupScriptProbeLeavesTheApplicationsStdoutOpen() throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream applicationStdout = new PrintStream(captured, true, "UTF-8");
    System.setOut(applicationStdout);
    try {
      SharedSettings settings = new SharedSettings();
      settings.setOutputFile("::stdout");
      FileClient client =
          new FileClient(new ClientContext(null, null, new ArgsMap(), settings), false);
      String internalName = deployForTest(client);
      client.setRuntimeForTest(commandThreadRuntime(client));
      BudgetGovernor governor = governor(limits("10", null), client);

      now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
      governor.tick();
      assertTrue(client.isShuttingDown(), "the exit did not remove the probe");

      applicationStdout.println("application output after the removal");
      assertFalse(applicationStdout.checkError(), "removing the probe closed System.out");
      String written = new String(captured.toByteArray(), StandardCharsets.UTF_8);
      assertTrue(
          written.contains(
              "BTrace: probe " + internalName.replace('/', '.') + " removed: lifetime exceeded"),
          "the reason was not written to stdout:\n" + written);
      assertTrue(written.contains("application output after the removal"), written);
    } finally {
      System.setOut(original);
    }
  }

  @Test
  void oneFailingClientDoesNotStopTheOthers() throws Exception {
    FakeClient failing = new FakeClient();
    failing.failure = new IllegalStateException("exit failed");
    FakeClient hot = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), failing, hot);

    governor.tick();
    failing.hit(5000);
    hot.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, failing.exits.size());
    assertEquals(1, hot.exits.size());
    now[0] += ONE_SECOND;
    assertDoesNotThrow(governor::tick);
  }

  @Test
  void reasonNamesTheProbeAsDeclared() throws Exception {
    FakeClient client = new FakeClient("HotPathProbe");
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size());
    assertTrue(client.exits.get(0).startsWith("probe HotPathProbe removed: "), client.exits.get(0));
  }

  @Test
  void reasonKeepsTheProbesOwnPackage() throws Exception {
    FakeClient client = new FakeClient("com.example.MyProbe");
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size());
    assertTrue(
        client.exits.get(0).startsWith("probe com.example.MyProbe removed: "), client.exits.get(0));
  }

  @Test
  void tripHappensOnce() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size());
  }

  @Test
  void hotProbeTripsQuietProbeStays() throws Exception {
    FakeClient hot = new FakeClient();
    FakeClient quiet = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), hot, quiet);

    governor.tick();
    hot.hit(5000);
    quiet.hit(10);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, hot.exits.size());
    assertTrue(quiet.exits.isEmpty());
    assertFalse(ProbeBudgets.isTripped(quiet.internalName));
  }

  @Test
  void neighbourRateIgnoresTimeSpentRemovingAnotherProbe() throws Exception {
    FakeClient slow = new FakeClient();
    FakeClient steady = new FakeClient();
    // Removing the slow probe takes 2 s, during which its neighbour keeps running at 400 hits/s.
    slow.onBudgetExit =
        () -> {
          now[0] += 2 * ONE_SECOND;
          steady.hit(800);
        };
    BudgetGovernor governor = governor(limits(null, "1000"), slow, steady);

    governor.tick();
    slow.hit(5000);
    steady.hit(400);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, slow.exits.size());
    assertTrue(steady.exits.isEmpty(), "a probe at 40 % of the ceiling was removed");
    assertFalse(ProbeBudgets.isTripped(steady.internalName));
  }

  @Test
  void blockedExitDoesNotStopTheNextTrip() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch hotExited = new CountDownLatch(1);
    // Set only once the blocking exit has stopped waiting, so the order in which the two exit
    // threads start does not matter.
    AtomicBoolean blockingFinished = new AtomicBoolean();
    AtomicBoolean whileBlocked = new AtomicBoolean();
    FakeClient blocking = new FakeClient();
    blocking.onBudgetExit =
        () -> {
          try {
            // Long enough that a slow machine starts the hot exit thread before it times out.
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            blockingFinished.set(true);
          }
        };
    FakeClient hot = new FakeClient();
    hot.onBudgetExit =
        () -> {
          whileBlocked.set(!blockingFinished.get());
          hotExited.countDown();
        };
    BudgetGovernor governor =
        new BudgetGovernor(
            limits(null, "1000"), () -> now[0], () -> Arrays.asList(blocking, hot), THREADS);
    try {
      governor.tick();
      blocking.hit(5000);
      hot.hit(5000);
      now[0] += ONE_SECOND;
      governor.tick();

      assertTrue(hotExited.await(5, TimeUnit.SECONDS), "the second probe was never removed");
      assertTrue(whileBlocked.get(), "the second probe waited for the first probe's exit");
      assertTrue(ProbeBudgets.isTripped(blocking.internalName));
      assertTrue(ProbeBudgets.isTripped(hot.internalName));
    } finally {
      release.countDown();
    }
  }

  @Test
  void interruptDoesNotStopTheGovernor() throws Exception {
    FakeClient hot = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), hot);

    governor.tick();
    hot.hit(5000);
    now[0] += ONE_SECOND;
    Thread.currentThread().interrupt();
    boolean keepRunning;
    try {
      keepRunning = governor.runOnce();
    } finally {
      Thread.interrupted();
    }

    assertTrue(keepRunning, "the governor loop ends on an interrupt");
    assertEquals(1, hot.exits.size(), "the interrupted pass did not check the probes");
  }

  @Test
  void throwingAccessorDoesNotStopTheGovernor() throws Exception {
    FakeClient broken = new FakeClient();
    broken.accessorFailure = new IllegalStateException("accessor failed");
    FakeClient hot = new FakeClient();
    AtomicInteger calls = new AtomicInteger();
    BudgetGovernor governor =
        new BudgetGovernor(
            limits(null, "1000"),
            () -> now[0],
            () -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("client list failed");
              }
              return Arrays.asList(broken, hot);
            },
            Runnable::run);

    assertTrue(governor.runOnce(), "a failing client list ended the governor loop");
    governor.tick();
    hot.hit(5000);
    now[0] += ONE_SECOND;
    assertTrue(governor.runOnce(), "a failing client accessor ended the governor loop");

    assertEquals(1, hot.exits.size());
  }

  @Test
  void stalledExitIsForcedAfterGracePeriod() throws Exception {
    // The fake's budgetExit only records the reason, so the probe never leaves on its own.
    FakeClient stalled = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), stalled);

    governor.tick();
    stalled.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    assertEquals(1, stalled.exits.size());

    now[0] += ONE_SECOND;
    governor.tick();
    assertEquals(0, stalled.onExitCalls.get(), "removal was forced before the grace period");

    now[0] += 5 * ONE_SECOND;
    governor.tick();
    assertEquals(1, stalled.onExitCalls.get(), "a stalled exit was never forced");
    assertTrue(stalled.isShuttingDown());

    now[0] += 10 * ONE_SECOND;
    governor.tick();
    assertEquals(1, stalled.onExitCalls.get(), "removal was forced more than once");
    assertEquals(1, stalled.exits.size(), "the trip was reported more than once");
  }

  @Test
  void forcedRemovalIsRetriedWhenItCannotBeStarted() throws Exception {
    FakeClient stalled = new FakeClient();
    AtomicInteger executions = new AtomicInteger();
    // The first task (the exit) runs; starting the second (the forced removal) fails once, as
    // when the JVM cannot create a thread.
    Executor flaky =
        task -> {
          if (executions.incrementAndGet() == 2) {
            throw new IllegalStateException("cannot create thread");
          }
          task.run();
        };
    BudgetGovernor governor =
        new BudgetGovernor(limits(null, "1000"), () -> now[0], () -> Arrays.asList(stalled), flaky);

    governor.tick();
    stalled.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    assertEquals(1, stalled.exits.size());

    now[0] += 6 * ONE_SECOND;
    governor.tick();
    assertEquals(0, stalled.onExitCalls.get());
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, stalled.onExitCalls.get(), "a forced removal that failed to start was dropped");
  }

  @Test
  void tripThatFailsHalfwayIsStillCompleted() throws Exception {
    FakeClient client = new FakeClient();
    AtomicInteger clockReads = new AtomicInteger();
    AtomicBoolean failOnce = new AtomicBoolean(true);
    // The clock fails on its second read in the tripping pass: the first read decides the trip,
    // the second is taken while the trip is recorded.
    BudgetGovernor governor =
        new BudgetGovernor(
            limits("10", null),
            () -> {
              if (clockReads.incrementAndGet() == 2 && failOnce.getAndSet(false)) {
                throw new IllegalStateException("clock failed");
              }
              return now[0];
            },
            () -> Arrays.asList(client),
            Runnable::run);

    now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
    governor.tick();
    governor.tick();
    now[0] += 6 * ONE_SECOND;
    governor.tick();

    assertTrue(
        client.exits.size() == 1 || client.onExitCalls.get() == 1,
        "a probe whose trip failed halfway was never removed: exits="
            + client.exits
            + ", forced="
            + client.onExitCalls.get());
  }

  @Test
  void failedExitIsForcedAfterGracePeriod() throws Exception {
    FakeClient failing = new FakeClient();
    failing.failure = new IllegalStateException("exit failed");
    BudgetGovernor governor = governor(limits("10", null), failing);

    now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
    governor.tick();
    assertEquals(1, failing.exits.size());
    now[0] += 6 * ONE_SECOND;
    governor.tick();

    assertEquals(1, failing.onExitCalls.get(), "a failed exit was never forced");
  }

  @Test
  void lostTransportIsForcedWithoutWaitingForGracePeriod() throws Exception {
    FakeClient dropped = new FakeClient();
    // The transport goes away after the exit was started, so the runtime's exit is discarded.
    dropped.onBudgetExit = () -> dropped.lost = true;
    BudgetGovernor governor = governor(limits(null, "1000"), dropped);

    governor.tick();
    dropped.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, dropped.onExitCalls.get(), "a probe that lost its client was not removed");
  }

  @Test
  void properExitIsNotForced() throws Exception {
    FakeClient proper = new FakeClient();
    proper.exitRemovesProbe = true;
    BudgetGovernor governor = governor(limits(null, "1000"), proper);

    governor.tick();
    proper.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    for (int i = 0; i < 10; i++) {
      now[0] += ONE_SECOND;
      governor.tick();
    }

    assertEquals(1, proper.onExitCalls.get(), "a probe that exited normally was forced");
  }

  @Test
  void exitStillRunningIsNotForcedEarly() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    FakeClient slow = new FakeClient();
    // A healthy client whose probe's @OnExit handlers are slow: the exit ends in time on its own.
    slow.exitRemovesProbe = true;
    slow.onBudgetExit =
        () -> {
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    BudgetGovernor governor =
        new BudgetGovernor(limits(null, "1000"), () -> now[0], () -> Arrays.asList(slow), THREADS);
    try {
      governor.tick();
      slow.hit(5000);
      now[0] += ONE_SECOND;
      governor.tick();
      now[0] += 10 * ONE_SECOND;
      governor.tick();
      Thread.sleep(200);

      assertEquals(0, slow.onExitCalls.get(), "an exit still in progress was cut short");
    } finally {
      release.countDown();
    }
  }

  @Test
  void blockedExitIsForcedAtTheHardDeadline() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch exitFinished = new CountDownLatch(1);
    FakeClient blocked = new FakeClient();
    // Like a detached RemoteClient: the exit ends by calling onExit itself, here after blocking.
    blocked.exitRemovesProbe = true;
    blocked.onBudgetExit =
        () -> {
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    blocked.afterBudgetExit = exitFinished::countDown;
    BudgetGovernor governor =
        new BudgetGovernor(
            limits(null, "1000"), () -> now[0], () -> Arrays.asList(blocked), THREADS);
    try {
      governor.tick();
      blocked.hit(5000);
      now[0] += ONE_SECOND;
      governor.tick();
      now[0] += 31 * ONE_SECOND;
      governor.tick();

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!blocked.isShuttingDown() && System.nanoTime() < deadline) {
        Thread.sleep(20);
      }
      assertTrue(blocked.isShuttingDown(), "a blocked exit kept the probe installed");
    } finally {
      release.countDown();
    }
    assertTrue(exitFinished.await(5, TimeUnit.SECONDS));
    assertEquals(2, blocked.onExitCalls.get());
    assertEquals(1, blocked.effectiveExits.get(), "the probe was finalized more than once");
  }

  /** A handler for {@link #tripStopsAnAlreadyLinkedCallSite()}; public so the resolver finds it. */
  public static void linkedHandler() {
    LINKED_HANDLER_RUNS.incrementAndGet();
  }

  private static final AtomicInteger LINKED_HANDLER_RUNS = new AtomicInteger();

  @Test
  void tripStopsAnAlreadyLinkedCallSite() throws Throwable {
    FakeClient client = new FakeClient();
    BTraceProbe linkable = linkableProbe(client.internalName);
    HandlerRepositoryImpl.registerProbe(linkable);
    try {
      CallSite site =
          IndyDispatcher.bootstrap(
              MethodHandles.lookup(),
              "Probe$linkedHandler",
              MethodType.methodType(void.class),
              client.internalName);
      MethodHandle invoker = site.dynamicInvoker();
      int before = LINKED_HANDLER_RUNS.get();
      invoker.invokeExact();
      assertEquals(before + 1, LINKED_HANDLER_RUNS.get(), "the call site was not linked");

      BudgetGovernor governor = governor(limits("10", null), client);
      now[0] = DEPLOYED_AT + 11 * ONE_SECOND;
      governor.tick();
      assertEquals(1, client.exits.size());

      invoker.invokeExact();
      assertEquals(
          before + 1, LINKED_HANDLER_RUNS.get(), "a call site linked before the trip still ran");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(linkable);
    }
  }

  /** A probe the handler resolver can link: its handlers are this test class's static methods. */
  private static BTraceProbe linkableProbe(String internalName) {
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BudgetGovernorTest.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getClassName":
                  return internalName;
                case "getProbeClass":
                  return BudgetGovernorTest.class;
                case "onmethods":
                  return Collections.emptyList();
                default:
                  return method.getReturnType() == Boolean.TYPE ? Boolean.FALSE : null;
              }
            });
  }

  /** Runs each task on a daemon thread of its own, as the governor does in production. */
  private static final Executor THREADS =
      task -> {
        Thread thread = new Thread(task, "test budget exit");
        thread.setDaemon(true);
        thread.start();
      };

  @Test
  void tripMarkOfAClientThatIsGoneIsDropped() throws Exception {
    FakeClient client = new FakeClient();
    Collection<Client> live = new ArrayList<>();
    live.add(client);
    BudgetGovernor governor =
        new BudgetGovernor(
            limits(null, "1000"), () -> now[0], () -> new ArrayList<>(live), Runnable::run);

    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();
    assertTrue(ProbeBudgets.isTripped(client.internalName));

    // The client's own exit already unregistered the probe, so only the governor can drop a
    // mark it set afterwards.
    live.clear();
    governor.tick();
    assertFalse(ProbeBudgets.isTripped(client.internalName), "a tripped mark outlived its probe");
  }

  @Test
  void probeThatStartsCountingAfterTheFirstTickIsStillTripped() throws Exception {
    FakeClient client = new FakeClient();
    BudgetGovernor governor = governor(limits(null, "1000"), client);

    // No handler has been resolved yet, so the probe has no counter: the baseline is zero.
    governor.tick();
    client.hit(5000);
    now[0] += ONE_SECOND;
    governor.tick();

    assertEquals(1, client.exits.size(), "a probe with no counter at the first tick was skipped");
  }

  private BudgetGovernor governor(BudgetLimits limits, Client... clients) {
    Collection<Client> all = Arrays.asList(clients);
    return new BudgetGovernor(limits, () -> now[0], () -> all, Runnable::run);
  }

  private static BudgetLimits limits(String ttl, String rate) throws Exception {
    Properties p = new Properties();
    if (ttl != null) {
      p.setProperty(BudgetLimits.TTL_PROPERTY, ttl);
    }
    if (rate != null) {
      p.setProperty(BudgetLimits.MAX_RATE_PROPERTY, rate);
    }
    return BudgetLimits.resolve(BudgetLimits.snapshot(p), new ArgsMap(), true, s -> {});
  }

  /** Gives {@code client} a deployed test probe of its own; returns the probe's internal name. */
  private static String deployForTest(Client client) {
    String className = "test.budget.FileProbe" + PROBE_SEQ.incrementAndGet();
    client.setProbeForTest(probe(className), DEPLOYED_AT);
    return className.replace('.', '/');
  }

  /**
   * A runtime that behaves like the real one towards {@code client}: commands reach the client's
   * {@code onCommand}, and an exit delivers the terminal marker and then the exit command from a
   * command thread of its own.
   */
  private static BTraceRuntime.Impl commandThreadRuntime(Client client) {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            BudgetGovernorTest.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              if ("sendCommand".equals(method.getName())) {
                client.onCommand((Command) args[0]);
              } else if ("handleExit".equals(method.getName())) {
                int exitCode = (Integer) args[0];
                Thread commandThread =
                    new Thread(
                        () -> {
                          try {
                            client.onCommand(MessageCommand.line("[BTRACE] terminal cleanup"));
                            client.onCommand(new ExitCommand(exitCode));
                          } catch (IOException e) {
                            throw new UncheckedIOException(e);
                          }
                        },
                        "test command thread");
                commandThread.setDaemon(true);
                commandThread.start();
                commandThread.join(TimeUnit.SECONDS.toMillis(5));
              }
              Class<?> type = method.getReturnType();
              if (type == Boolean.TYPE) return Boolean.FALSE;
              if (type == Integer.TYPE) return Integer.valueOf(0);
              if (type == Long.TYPE) return Long.valueOf(0L);
              return null;
            });
  }

  /** A probe proxy that answers only the names; every other method returns a default value. */
  static BTraceProbe probe(String className) {
    return probe(className, className);
  }

  /** As {@link #probe(String)}, with the name the probe declared before the agent renamed it. */
  static BTraceProbe probe(String className, String declaredName) {
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BudgetGovernorTest.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              if ("getClassName".equals(method.getName())) {
                boolean internal = args != null && (Boolean) args[0];
                return internal ? className.replace('.', '/') : className;
              }
              if ("getDeclaredClassName".equals(method.getName())) {
                return declaredName;
              }
              Class<?> type = method.getReturnType();
              if (type == Boolean.TYPE) return Boolean.FALSE;
              return null;
            });
  }

  private final class FakeClient extends Client {
    final String className;
    final String internalName;
    final List<String> exits = new ArrayList<>();
    boolean exempt;
    RuntimeException failure;
    RuntimeException accessorFailure;
    Runnable onBudgetExit;
    Runnable afterBudgetExit;
    boolean exitRemovesProbe;
    volatile boolean lost;
    final AtomicInteger onExitCalls = new AtomicInteger();
    final AtomicInteger effectiveExits = new AtomicInteger();
    volatile boolean trippedWhenExited;

    FakeClient() {
      this(null);
    }

    /** A probe whose loaded name is renamed into the auxiliary package; null keeps a test name. */
    FakeClient(String declaredName) {
      super(new ClientContext(null, null, new ArgsMap(), new SharedSettings()));
      String seq = String.valueOf(PROBE_SEQ.incrementAndGet());
      className =
          declaredName == null
              ? "test.budget.Probe" + seq
              : "io.btrace.runtime.auxiliary." + declaredName + seq;
      internalName = className.replace('.', '/');
      setProbeForTest(
          probe(className, declaredName == null ? className : declaredName), DEPLOYED_AT);
    }

    void hit(long hits) {
      ProbeBudgets.counter(internalName).add(hits);
    }

    @Override
    public void onCommand(Command cmd) {}

    @Override
    boolean budgetExempt() {
      if (accessorFailure != null) {
        throw accessorFailure;
      }
      return exempt;
    }

    @Override
    void budgetExit(String reason) {
      trippedWhenExited = ProbeBudgets.isTripped(internalName);
      synchronized (exits) {
        exits.add(reason);
      }
      try {
        if (onBudgetExit != null) {
          onBudgetExit.run();
        }
        if (failure != null) {
          throw failure;
        }
        if (exitRemovesProbe) {
          onExit(BUDGET_EXIT_CODE);
        }
      } finally {
        if (afterBudgetExit != null) {
          afterBudgetExit.run();
        }
      }
    }

    @Override
    boolean transportLost() {
      return lost;
    }

    @Override
    synchronized void onExit(int exitCode) {
      onExitCalls.incrementAndGet();
      if (!isShuttingDown()) {
        effectiveExits.incrementAndGet();
      }
      super.onExit(exitCode);
    }
  }
}
