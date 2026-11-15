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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import io.btrace.client.Client;
import io.btrace.core.comm.ListProbesCommand;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tests.harness.TargetRegistry;

/**
 * End-to-end coverage for operator-set probe budgets ({@code probeMaxRate}, {@code probeTtl}).
 *
 * <p>Each test starts a prepared target ({@code -javaagent:btrace.jar=port=0,...}) so the operator
 * sets the limits, then deploys probes through the real {@code btrace} CLI in its own JVM, as a
 * user would. Startup-script probes are exempt from budgets only when the agent is loaded at
 * launch; {@link #attachTimeStartupScriptsAreBudgetedAndVerified()} loads the agent by dynamic
 * attach with {@code script=}.
 */
public class ProbeBudgetIntegrationTest extends RuntimeTest {
  private static final String TARGET = "resources.BudgetLoopTarget";
  private static final String HOT_PROBE = "btrace/BudgetHotProbe.java";
  private static final String QUIET_PROBE = "btrace/BudgetQuietProbe.java";

  /** Exit code of a CLI client whose probe was removed by a budget. */
  private static final int BUDGET_EXIT_CODE = 5;

  /** The governor ticks once a second and needs a baseline tick first; 30 s is ample headroom. */
  private static final long REMOVAL_DEADLINE_MS = 30_000L;

  private static final Pattern HEARTBEAT = Pattern.compile("^heartbeat:(\\d+)$");

  private static final Pattern TRUSTED_REFUSED =
      Pattern.compile(
          "^BTrace: trusted mode is not available: this JVM has a probe budget set by its"
              + " operator\\s*$");

  /** Exit code of a CLI client whose trusted-mode deploy was refused. */
  private static final int TRUSTED_REFUSED_EXIT_CODE = 1;

  /** The line the agent logs when it ignores an attach-time {@code trusted=true}. */
  private static final String TRUSTED_ARGUMENT_IGNORED =
      "ignoring agent argument trusted=true, every probe is verified";

  /** The line the agent prints when it is loaded into a JVM that already has it. */
  private static final String AGENT_ALREADY_LOADED = "BTrace agent is already loaded in this JVM";

  /** Exit code of {@code btrace doctor} when the target publishes no control endpoint. */
  private static final int DOCTOR_PREPARATION_REQUIRED = 2;

  /**
   * Debug logging for the agent's {@code Client} logger only. The masked JAR relocates slf4j, so
   * the logger configuration keys carry the relocated package.
   */
  private static final String CLIENT_DEBUG_LOGGING =
      "-Dio.btrace.libs.org.slf4j.simpleLogger.log.io.btrace.agent.Client=debug";

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void hotProbeIsRemovedWithReasonAndExitCode() throws Exception {
    // Debug logging for the agent's Client only: it names each class retransformed on removal.
    TestApp app = launchTarget("probeMaxRate=100", CLIENT_DEBUG_LOGGING);
    CliRun hot = null;
    try {
      int pid = app.getPid();
      hot = deploy(pid, HOT_PROBE, false);

      String removal = hot.awaitLine(removalPattern("BudgetHotProbe", "100"), REMOVAL_DEADLINE_MS);
      assertNotNull(
          removal, "client never reported the hot probe's removal:\n" + hot.output() + app(app));
      assertEquals(
          BUDGET_EXIT_CODE,
          hot.awaitExit(15),
          "client must exit with the budget exit code:\n" + hot.output());

      assertTrue(
          awaitOutput(app, "BTrace: probe btrace.BudgetHotProbe removed: ", 10_000L),
          "target did not log the removal:\n" + app.capturedOutput());
      // The probe listing shows detached probes only, so it cannot show this removal. What does:
      // after the removal line, the agent retransforms the instrumented class with the probe's
      // transformer already unregistered, which restores the class's original code.
      assertTrue(
          awaitOutputAfter(
              app,
              "BTrace: probe btrace.BudgetHotProbe removed: ",
              "Attempting to retransform class: " + TARGET,
              10_000L),
          "target did not retransform " + TARGET + " after the removal:" + app(app));
      assertTargetStillRunning(app);
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void quietProbeSurvivesNextToHotProbe() throws Exception {
    TestApp app = launchTarget("probeMaxRate=100");
    CliRun quiet = null;
    CliRun hot = null;
    try {
      int pid = app.getPid();
      quiet = deploy(pid, QUIET_PROBE, false);
      assertNotNull(
          quiet.awaitLine(Pattern.compile("^budget-quiet-hit$"), REMOVAL_DEADLINE_MS),
          "quiet probe produced no output:\n" + quiet.output());

      hot = deploy(pid, HOT_PROBE, false);
      assertNotNull(
          hot.awaitLine(removalPattern("BudgetHotProbe", "100"), REMOVAL_DEADLINE_MS),
          "client never reported the hot probe's removal:\n" + hot.output() + app(app));
      assertEquals(BUDGET_EXIT_CODE, hot.awaitExit(15), "hot client exit code:\n" + hot.output());

      // The quiet probe must keep running after its neighbour was removed.
      int quietHitsAtRemoval = quiet.count("budget-quiet-hit");
      long deadline = System.currentTimeMillis() + 10_000L;
      while (quiet.count("budget-quiet-hit") < quietHitsAtRemoval + 3
          && System.currentTimeMillis() < deadline) {
        Thread.sleep(100);
      }
      assertTrue(
          quiet.count("budget-quiet-hit") >= quietHitsAtRemoval + 3,
          "quiet probe stopped producing output after the hot probe was removed:\n"
              + quiet.output());
      assertTrue(quiet.isAlive(), "quiet client exited:\n" + quiet.output());
      assertFalse(
          quiet.output().contains("removed:"),
          "quiet probe was reported as removed:\n" + quiet.output());
      assertFalse(
          app.capturedOutput().contains("BudgetQuietProbe removed:"),
          "target removed the quiet probe:\n" + app.capturedOutput());
    } finally {
      destroy(hot);
      destroy(quiet);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void detachedProbeIsRemovedByTtl() throws Exception {
    // 15 s leaves room for the CLI JVM to exit and for the first listing before the TTL expires.
    TestApp app = launchTarget("probeTtl=15");
    CliRun deployer = null;
    try {
      int pid = app.getPid();
      deployer = deploy(pid, QUIET_PROBE, true);
      assertEquals(0, deployer.awaitExit(30), "unattended deploy failed:\n" + deployer.output());
      long detachedAt = System.currentTimeMillis();

      // The listing reports detached probes only, so seeing the probe first proves the empty
      // listing below is the removal and not a probe that never got there.
      boolean seen = false;
      boolean gone = false;
      List<String> probes = new ArrayList<>();
      while (System.currentTimeMillis() - detachedAt < 45_000L) {
        probes = listDetachedProbes(pid);
        if (containsProbe(probes, "BudgetQuietProbe")) {
          seen = true;
        } else if (seen) {
          gone = true;
          break;
        }
        Thread.sleep(250);
      }
      assertTrue(seen, "detached probe never appeared in the probe listing: " + probes);
      assertTrue(gone, "detached probe still listed 45 s after detach: " + probes + app(app));
      assertTrue(
          awaitOutput(
              app,
              "BTrace: probe btrace.BudgetQuietProbe removed: lifetime exceeded probeTtl=15s",
              5_000L),
          "target did not log the TTL removal:\n" + app.capturedOutput());
      assertTargetStillRunning(app);
    } finally {
      destroy(deployer);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void launchArgumentCannotRaiseOperatorCeiling() throws Exception {
    TestApp app = launchTarget("probeMaxRate=100000000", "-Dio.btrace.probeMaxRate=100");
    CliRun hot = null;
    try {
      int pid = app.getPid();
      hot = deploy(pid, HOT_PROBE, false);
      // Anchored at the end of the line: "probeMaxRate=100000000" also starts with "...=100".
      assertNotNull(
          hot.awaitLine(removalPattern("BudgetHotProbe", "100"), REMOVAL_DEADLINE_MS),
          "hot probe was not removed at the operator's ceiling of 100:\n"
              + hot.output()
              + app(app));
      assertEquals(BUDGET_EXIT_CODE, hot.awaitExit(15), "client exit code:\n" + hot.output());
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void dollarArgumentCannotOverwriteOperatorProperty() throws Exception {
    // "$key=value" agent arguments become system properties; the budget properties are refused.
    TestApp app = launchTarget("$io.btrace.probeMaxRate=100000000", "-Dio.btrace.probeMaxRate=100");
    CliRun hot = null;
    try {
      int pid = app.getPid();
      assertTrue(
          awaitOutput(app, "ignoring agent argument $io.btrace.probeMaxRate", 10_000L),
          "agent did not refuse the $io.btrace.probeMaxRate argument:" + app(app));
      hot = deploy(pid, HOT_PROBE, false);
      assertNotNull(
          hot.awaitLine(removalPattern("BudgetHotProbe", "100"), REMOVAL_DEADLINE_MS),
          "hot probe was not removed at the operator's ceiling of 100:\n"
              + hot.output()
              + app(app));
      assertEquals(BUDGET_EXIT_CODE, hot.awaitExit(15), "client exit code:\n" + hot.output());
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void agentArgumentTightensOperatorCeiling() throws Exception {
    TestApp app = launchTarget("probeMaxRate=50", "-Dio.btrace.probeMaxRate=100");
    CliRun hot = null;
    try {
      int pid = app.getPid();
      hot = deploy(pid, HOT_PROBE, false);
      assertNotNull(
          hot.awaitLine(removalPattern("BudgetHotProbe", "50"), REMOVAL_DEADLINE_MS),
          "hot probe was not removed at the tighter limit of 50:\n" + hot.output() + app(app));
      assertEquals(BUDGET_EXIT_CODE, hot.awaitExit(15), "client exit code:\n" + hot.output());
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void trustedDeployIsRefusedUnderBudget() throws Exception {
    TestApp app = launchTarget("probeMaxRate=100");
    CliRun trusted = null;
    CliRun verified = null;
    try {
      int pid = app.getPid();
      trusted = deploy(pid, QUIET_PROBE, false, "-u");
      assertNotNull(
          trusted.awaitLine(TRUSTED_REFUSED, REMOVAL_DEADLINE_MS),
          "trusted deploy was not refused:\n" + trusted.output() + app(app));
      assertEquals(
          TRUSTED_REFUSED_EXIT_CODE,
          trusted.awaitExit(15),
          "refused client exit code:\n" + trusted.output());
      assertEquals(
          0, trusted.count("budget-quiet-hit"), "the refused probe ran:\n" + trusted.output());
      assertTargetStillRunning(app);

      // The refusal leaves the target usable: a verified deploy of the same probe still works.
      verified = deploy(pid, QUIET_PROBE, false);
      assertNotNull(
          verified.awaitLine(Pattern.compile("^budget-quiet-hit$"), REMOVAL_DEADLINE_MS),
          "verified deploy after the refusal produced no output:\n" + verified.output() + app(app));
    } finally {
      destroy(verified);
      destroy(trusted);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void trustedDeployIsRefusedUnderBudgetWithDynamicAttach() throws Exception {
    // No agent at launch: the client loads it and passes trusted=true as an agent argument. The
    // budget comes from the operator's system property.
    TestApp app =
        launchTestAppWithAgent(
            TARGET,
            null,
            "-Dio.btrace.probeMaxRate=100",
            "-XX:+EnableDynamicAgentLoading",
            "-Djdk.attach.allowAttachSelf=true",
            "-Dbtrace.comm.protocol=2",
            "-Dbtrace.comm.autoNegotiate=false",
            "-Dbtrace.comm.forceVersion=true");
    CliRun trusted = null;
    CliRun verified = null;
    try {
      int pid = app.getPid();
      String port = String.valueOf(freePort());
      trusted = deploy(pid, QUIET_PROBE, false, "-u", "-p", port);
      assertNotNull(
          trusted.awaitLine(TRUSTED_REFUSED, REMOVAL_DEADLINE_MS),
          "trusted deploy was not refused:\n" + trusted.output() + app(app));
      assertEquals(
          TRUSTED_REFUSED_EXIT_CODE,
          trusted.awaitExit(15),
          "refused client exit code:\n" + trusted.output());
      assertEquals(
          0, trusted.count("budget-quiet-hit"), "the refused probe ran:\n" + trusted.output());
      assertTrue(
          awaitOutput(app, TRUSTED_ARGUMENT_IGNORED, 10_000L),
          "agent did not report ignoring the attach-time trusted=true:" + app(app));
      assertTargetStillRunning(app);

      // The attaching client's trusted=true agent argument must not stick to later clients.
      verified = deploy(pid, QUIET_PROBE, false, "-p", port);
      assertNotNull(
          verified.awaitLine(Pattern.compile("^budget-quiet-hit$"), REMOVAL_DEADLINE_MS),
          "verified deploy after the refusal produced no output:\n" + verified.output() + app(app));
    } finally {
      destroy(verified);
      destroy(trusted);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void attachTimeStartupScriptsAreBudgetedAndVerified() throws Exception {
    // No agent at launch: the attacher loads it with script= and trusted=true. The budget comes
    // from the operator's system property.
    TestApp app =
        launchTestAppWithAgent(
            TARGET,
            null,
            "-Dio.btrace.probeMaxRate=100",
            CLIENT_DEBUG_LOGGING,
            "-XX:+EnableDynamicAgentLoading",
            "-Dbtrace.comm.protocol=2",
            "-Dbtrace.comm.autoNegotiate=false",
            "-Dbtrace.comm.forceVersion=true");
    try {
      int pid = app.getPid();
      File trustedScript = locateTrace("btrace/BudgetTrustedProbe.class");
      File hotScript = locateTrace("btrace/BudgetHotProbe.class");
      assertNotNull(trustedScript, "compiled probe is missing: BudgetTrustedProbe.class");
      assertNotNull(hotScript, "compiled probe is missing: BudgetHotProbe.class");
      loadAgentByAttach(
          pid,
          "telemetry=false,port="
              + freePort()
              + ",trusted=true,scriptOutputFile=::stdout,script="
              + trustedScript.getAbsolutePath()
              + ":"
              + hotScript.getAbsolutePath());

      assertTrue(
          awaitOutput(app, TRUSTED_ARGUMENT_IGNORED, 10_000L),
          "agent did not report ignoring the attach-time trusted=true:" + app(app));
      // A probe that needs trusted mode is verified, so it is rejected instead of running.
      assertTrue(
          awaitOutput(
              app, "Unable to load BTrace script " + trustedScript.getAbsolutePath(), 10_000L),
          "the trusted startup script was not rejected:" + app(app));

      String removal = "BTrace: probe btrace.BudgetHotProbe removed: ";
      assertTrue(
          awaitOutput(app, removal, REMOVAL_DEADLINE_MS),
          "the attach-time startup script was not removed by the budget:" + app(app));
      assertTrue(
          app.capturedOutput().contains("exceeded probeMaxRate=100"),
          "the removal does not name the rate ceiling:" + app(app));
      assertTrue(
          awaitOutputAfter(app, removal, "Attempting to retransform class: " + TARGET, 10_000L),
          "target did not retransform " + TARGET + " after the removal:" + app(app));
      // The probe's timer stops with it.
      int aliveAtRemoval = count(app.capturedOutput(), "budget-hot-alive");
      Thread.sleep(3_000L);
      assertTrue(
          count(app.capturedOutput(), "budget-hot-alive") <= aliveAtRemoval + 1,
          "the removed probe kept running:" + app(app));
      assertFalse(
          app.capturedOutput().contains("budget-trusted-ran"),
          "a probe ran in trusted mode under a budget:" + app(app));
      assertTargetStillRunning(app);
    } finally {
      app.stop();
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void secondAgentLoadIsRefusedAndTheBudgetStillHolds() throws Exception {
    // The budget is set only by a launch-time agent argument. An attacher loads the agent again
    // with a startup script of its own: the load is refused, the script does not run, and the
    // agent loaded at launch keeps working and keeps enforcing its budget.
    TestApp app = launchTarget("probeMaxRate=100", "-XX:+EnableDynamicAgentLoading");
    CliRun hot = null;
    try {
      int pid = app.getPid();
      File hotScript = locateTrace("btrace/BudgetHotProbe.class");
      assertNotNull(hotScript, "compiled probe is missing: BudgetHotProbe.class");
      loadAgentByAttach(
          pid,
          "telemetry=false,port="
              + freePort()
              + ",scriptOutputFile=::stdout,script="
              + hotScript.getAbsolutePath());
      assertTrue(
          awaitOutput(app, AGENT_ALREADY_LOADED, 10_000L),
          "the second agent load was not refused:" + app(app));

      hot = deploy(pid, HOT_PROBE, false);
      assertNotNull(
          hot.awaitLine(removalPattern("BudgetHotProbe", "100"), REMOVAL_DEADLINE_MS),
          "the launch-time agent no longer enforces its budget:\n" + hot.output() + app(app));
      assertEquals(BUDGET_EXIT_CODE, hot.awaitExit(15), hot.output());
      // The refused load's startup script would print to the target's own output.
      assertEquals(
          0,
          count(app.capturedOutput(), "budget-hot-alive"),
          "the refused load ran its startup script:" + app(app));
      assertTargetStillRunning(app);
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void secondAgentLoadCannotRevokeLaunchTimeTrustedGrant() throws Exception {
    // The operator grants trusted mode at launch, next to a budget. A later agent load by
    // dynamic attach that passes trusted=false is refused and changes nothing.
    TestApp app =
        launchTarget("probeMaxRate=100000000,trusted=true", "-XX:+EnableDynamicAgentLoading");
    CliRun trusted = null;
    try {
      int pid = app.getPid();
      loadAgentByAttach(pid, "telemetry=false,trusted=false,port=" + freePort());
      assertTrue(
          awaitOutput(app, AGENT_ALREADY_LOADED, 10_000L),
          "the second agent load was not refused:" + app(app));

      // No -u: the probe needs trusted mode, which only the launch-time grant can give it.
      trusted = deploy(pid, "btrace/BudgetTrustedProbe.class", false);
      assertTrue(
          awaitOutput(app, "budget-trusted-ran", REMOVAL_DEADLINE_MS),
          "the launch-time trusted grant was revoked:\n" + trusted.output() + app(app));
      assertFalse(
          trusted.output().contains("trusted mode is not available"),
          "the operator's trusted grant was refused:\n" + trusted.output());
    } finally {
      destroy(trusted);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void attachTimeBudgetArgumentIsIgnored() throws Exception {
    // No operator budget. The attacher loads the agent with probeMaxRate=100: it is not the
    // operator, so the argument sets no budget and the hot probe keeps running.
    TestApp app =
        launchTestAppWithAgent(
            TARGET,
            null,
            "-XX:+EnableDynamicAgentLoading",
            "-Dbtrace.comm.protocol=2",
            "-Dbtrace.comm.autoNegotiate=false",
            "-Dbtrace.comm.forceVersion=true");
    try {
      int pid = app.getPid();
      File hotScript = locateTrace("btrace/BudgetHotProbe.class");
      assertNotNull(hotScript, "compiled probe is missing: BudgetHotProbe.class");
      loadAgentByAttach(
          pid,
          "telemetry=false,port="
              + freePort()
              + ",probeMaxRate=100,scriptOutputFile=::stdout,script="
              + hotScript.getAbsolutePath());

      assertTrue(
          awaitOutput(app, "ignoring agent argument probeMaxRate=100", 10_000L),
          "agent did not report ignoring the attach-time budget argument:" + app(app));
      assertTrue(
          awaitOutput(app, "budget-hot-alive", 15_000L),
          "the attach-time startup script did not run:" + app(app));
      // Several governor periods: a budget would have removed the probe by now.
      int alive = count(app.capturedOutput(), "budget-hot-alive");
      Thread.sleep(6_000L);
      assertTrue(
          count(app.capturedOutput(), "budget-hot-alive") > alive,
          "the probe stopped running:" + app(app));
      assertFalse(
          app.capturedOutput().contains("BTrace: probe btrace.BudgetHotProbe removed: "),
          "an attach-time argument set a budget:" + app(app));
      assertTargetStillRunning(app);
    } finally {
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void invalidOperatorBudgetStopsAttachTimeProbes() throws Exception {
    // The operator mistyped the limit; an attacher then loads the agent with a startup script
    // that runs only in trusted mode.
    TestApp app =
        launchTestAppWithAgent(
            TARGET,
            null,
            "-Dio.btrace.probeMaxRate=abc",
            "-XX:+EnableDynamicAgentLoading",
            "-Dbtrace.comm.protocol=2",
            "-Dbtrace.comm.autoNegotiate=false",
            "-Dbtrace.comm.forceVersion=true");
    try {
      int pid = app.getPid();
      File trustedScript = locateTrace("btrace/BudgetTrustedProbe.class");
      assertNotNull(trustedScript, "compiled probe is missing: BudgetTrustedProbe.class");
      loadAgentByAttach(
          pid,
          "telemetry=false,port="
              + freePort()
              + ",trusted=true,scriptOutputFile=::stdout,script="
              + trustedScript.getAbsolutePath());

      assertTrue(
          awaitOutput(app, "probeMaxRate=abc", 10_000L),
          "agent output does not name the invalid option probeMaxRate=abc:" + app(app));
      // The probe's timer fires every 500 ms; 3 s is ample time to see it if it was loaded.
      Thread.sleep(3_000L);
      assertFalse(
          app.capturedOutput().contains("budget-trusted-ran"),
          "an attach-time probe ran with an invalid operator budget:" + app(app));
      String output = app.capturedOutput();
      assertTrue(
          output.contains("attach-time probes were not loaded"),
          "agent output does not say the attach-time probes were not loaded:" + app(app));
      assertFalse(output.contains("FATAL"), "agent reported a fatal failure:" + app(app));
      assertFalse(
          output.contains("\tat io.btrace."),
          "agent printed a stack trace for a configuration error:" + app(app));
      assertTargetStillRunning(app);
    } finally {
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void trustedDeployWorksWithoutBudget() throws Exception {
    TestApp app = launchTarget(null);
    CliRun trusted = null;
    try {
      int pid = app.getPid();
      trusted = deploy(pid, QUIET_PROBE, false, "-u");
      assertNotNull(
          trusted.awaitLine(Pattern.compile("^budget-quiet-hit$"), REMOVAL_DEADLINE_MS),
          "trusted deploy without a budget produced no output:\n" + trusted.output() + app(app));
      assertTrue(trusted.isAlive(), "trusted client exited:\n" + trusted.output());
      assertFalse(
          trusted.output().contains("trusted mode is not available"),
          "trusted deploy was refused without a budget:\n" + trusted.output());
    } finally {
      destroy(trusted);
      app.stop();
    }
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void invalidLimitAtLaunchKeepsTargetRunningWithoutServer() throws Exception {
    TestApp app = launchTarget("probeMaxRate=abc");
    try {
      app.getPid();
      // stderr is read on its own thread and can trail the ready line on stdout.
      awaitOutput(app, "probeMaxRate=abc", 10_000L);
      String output = app.capturedOutput();
      assertTrue(
          output.contains("probeMaxRate=abc"),
          "agent output does not name the rejected option probeMaxRate=abc:\n" + output);
      assertTrue(
          output.contains("the command server was not started"),
          "agent output does not say the command server was not started:\n" + output);
      assertFalse(output.contains("FATAL"), "agent reported a fatal failure:\n" + output);
      assertFalse(
          output.contains("\tat io.btrace."),
          "agent printed a stack trace for a configuration error:\n" + output);
      // No command server means no published endpoint: a client cannot deploy anything.
      CliRun doctor = runCli("doctor", String.valueOf(app.getPid()));
      try {
        assertEquals(
            DOCTOR_PREPARATION_REQUIRED,
            doctor.awaitExit(30),
            "btrace doctor did not report a missing endpoint:\n" + doctor.output());
        assertTrue(
            doctor.output().contains("No BTrace control endpoint is published."),
            "btrace doctor found an endpoint:\n" + doctor.output());
      } finally {
        destroy(doctor);
      }
      assertTargetStillRunning(app);
    } finally {
      app.stop();
    }
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void noLimitsBehavesAsBefore() throws Exception {
    TestApp app = launchTarget(null);
    CliRun hot = null;
    try {
      int pid = app.getPid();
      hot = deploy(pid, HOT_PROBE, false);
      assertNotNull(
          hot.awaitLine(Pattern.compile("^budget-hot-alive$"), REMOVAL_DEADLINE_MS),
          "hot probe never reported it is attached:\n" + hot.output() + app(app));

      // Watch for 5 s: well past the 2 s a rate trip takes when a ceiling is set.
      long until = System.currentTimeMillis() + 5_000L;
      while (System.currentTimeMillis() < until && hot.isAlive()) {
        Thread.sleep(100);
      }
      assertTrue(hot.isAlive(), "client exited without a limit set:\n" + hot.output());
      int aliveLines = hot.count("budget-hot-alive");
      assertNotNull(
          hot.awaitCount("budget-hot-alive", aliveLines + 1, 5_000L),
          "hot probe stopped running without a limit set:\n" + hot.output());
      assertFalse(hot.output().contains("removed:"), "probe was removed:\n" + hot.output());

      // Stop the target, as the other functional tests do: the client then exits cleanly.
      app.stop();
      assertEquals(0, hot.awaitExit(15), "client exit code:\n" + hot.output());
    } finally {
      destroy(hot);
      app.stop();
    }
  }

  private TestApp launchTarget(String budgetArguments, String... extraJvmArguments)
      throws Exception {
    String agentArguments = "telemetry=false,port=0";
    if (budgetArguments != null) {
      agentArguments += "," + budgetArguments;
    }
    List<String> jvmArguments =
        new ArrayList<>(
            Arrays.asList(
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true"));
    jvmArguments.addAll(Arrays.asList(extraJvmArguments));
    return launchTestAppWithAgent(TARGET, agentArguments, jvmArguments.toArray(new String[0]));
  }

  /**
   * Matches the whole removal line for a rate trip at {@code max}. The probe declares {@code
   * package btrace}, so its name carries that package and nothing of the agent's internal one.
   */
  private static Pattern removalPattern(String probe, String max) {
    return Pattern.compile(
        "^BTrace: probe btrace\\."
            + probe
            + " removed: \\d+ hits/s exceeded probeMaxRate="
            + max
            + "\\s*$");
  }

  /** Starts the {@code btrace} CLI in its own JVM against the target, as a user would. */
  private CliRun deploy(int pid, String probe, boolean unattended, String... flags)
      throws IOException {
    File traceFile = locateTrace(probe);
    assertNotNull(traceFile, "probe source is missing: " + probe);
    List<String> arguments =
        new ArrayList<>(
            Arrays.asList(
                "-cp",
                getEventsClassPath(),
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-pd",
                traceFile.getParentFile().getAbsolutePath()));
    if (unattended) {
      arguments.add("-x");
    }
    arguments.addAll(Arrays.asList(flags));
    arguments.add(String.valueOf(pid));
    arguments.add(traceFile.getAbsolutePath());
    return runCli(probe, arguments);
  }

  private CliRun runCli(String... arguments) throws IOException {
    return runCli(arguments[0], Arrays.asList(arguments));
  }

  /** Runs the {@code btrace} CLI in its own JVM with {@code arguments}. */
  private CliRun runCli(String label, List<String> arguments) throws IOException {
    Path btraceJar = Paths.get(System.getProperty("btrace.libs"), "btrace.jar");
    String classpath = btraceJar.toString();
    Path toolsJar = Paths.get(javaHome, "lib", "tools.jar");
    if (Files.exists(toolsJar)) {
      classpath += File.pathSeparator + toolsJar;
    }
    List<String> command =
        new ArrayList<>(
            Arrays.asList(
                Paths.get(javaHome, "bin", "java").toString(),
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true",
                "-Dbtrace.libs=" + System.getProperty("btrace.libs"),
                "-Dbtrace.suppressJavaDeprecationWarning=true",
                "-cp",
                classpath,
                "io.btrace.boot.Loader"));
    if (Files.exists(Paths.get(javaHome, "lib", "modules"))) {
      command.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    command.addAll(arguments);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().remove("JAVA_TOOL_OPTIONS");
    builder.environment().remove("BTRACE_HOME");
    Process process = builder.start();
    TargetRegistry.register(process, "probe budget client", jcmdFor(javaHome));
    return new CliRun(process, label);
  }

  /**
   * Probes the agent lists; the agent lists only <em>detached</em> probes ({@code
   * io.btrace.agent.Client.listProbes}).
   */
  private List<String> listDetachedProbes(int pid) throws IOException {
    File traceFile = locateTrace(QUIET_PROBE);
    assertNotNull(traceFile, "probe source is missing: " + QUIET_PROBE);
    Client client = createClientForTests(traceFile.getParentFile().getAbsolutePath());
    List<String> probes = new ArrayList<>();
    try {
      client.attach(String.valueOf(pid), null, getEventsClassPath());
      client.connectAndListProbes(
          "localhost",
          cmd -> {
            if (cmd instanceof ListProbesCommand) {
              probes.addAll(((ListProbesCommand) cmd).getProbes());
            }
          });
    } finally {
      client.close();
    }
    return probes;
  }

  /** Loads the agent into {@code pid} by dynamic attach with {@code agentArguments}. */
  private void loadAgentByAttach(int pid, String agentArguments) throws Exception {
    Path agent = locateAgent();
    assertNotNull(agent, "btrace.jar is missing");
    VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
    try {
      vm.loadAgent(agent.toString(), agentArguments);
    } catch (AgentLoadException e) {
      // HotSpot on JDK 8 reports a successful load as AgentLoadException("...: 0"), as the BTrace
      // client also allows for. The callers' assertions on the target prove the agent ran.
      if (e.getMessage() == null || !e.getMessage().endsWith("0")) {
        throw e;
      }
    } finally {
      vm.detach();
    }
  }

  private static int count(String output, String expected) {
    int count = 0;
    for (String line : output.split("\n")) {
      if (line.trim().equals(expected)) {
        count++;
      }
    }
    return count;
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static boolean containsProbe(List<String> probes, String name) {
    for (String probe : probes) {
      if (probe.contains(name + "]")) {
        return true;
      }
    }
    return false;
  }

  private static boolean awaitOutput(TestApp app, String marker, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!app.capturedOutput().contains(marker)) {
      if (System.currentTimeMillis() >= deadline) {
        return false;
      }
      Thread.sleep(100);
    }
    return true;
  }

  /** Waits until {@code marker} appears in the target output after the first {@code anchor}. */
  private static boolean awaitOutputAfter(TestApp app, String anchor, String marker, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (true) {
      String output = app.capturedOutput();
      int at = output.indexOf(anchor);
      if (at >= 0 && output.indexOf(marker, at) >= 0) {
        return true;
      }
      if (System.currentTimeMillis() >= deadline) {
        return false;
      }
      Thread.sleep(100);
    }
  }

  /** The target keeps printing heartbeats: its JVM and its application thread are alive. */
  private static void assertTargetStillRunning(TestApp app) throws InterruptedException {
    long last = lastHeartbeat(app.capturedOutput());
    long deadline = System.currentTimeMillis() + 5_000L;
    while (lastHeartbeat(app.capturedOutput()) <= last && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    assertTrue(
        lastHeartbeat(app.capturedOutput()) > last,
        "target stopped running (no new heartbeat after heartbeat:"
            + last
            + "):\n"
            + app.capturedOutput());
  }

  private static long lastHeartbeat(String output) {
    long last = -1;
    for (String line : output.split("\n")) {
      Matcher matcher = HEARTBEAT.matcher(line.trim());
      if (matcher.matches()) {
        last = Math.max(last, Long.parseLong(matcher.group(1)));
      }
    }
    return last;
  }

  private static String app(TestApp app) {
    return "\n--- target output ---\n" + app.capturedOutput();
  }

  private static void destroy(CliRun run) throws InterruptedException {
    if (run != null) {
      run.destroy();
    }
  }

  /** A {@code btrace} CLI process with its output collected line by line. */
  private static final class CliRun {
    private final Process process;
    private final String label;
    private final List<String> lines = new ArrayList<>();

    CliRun(Process process, String label) {
      this.process = process;
      this.label = label;
      pump(process.getInputStream(), "stdout");
      pump(process.getErrorStream(), "stderr");
    }

    private void pump(InputStream stream, String name) {
      Thread reader =
          new Thread(
              () -> {
                try (BufferedReader in =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = in.readLine()) != null) {
                    System.out.println("[" + label + " " + name + "] " + line);
                    synchronized (lines) {
                      lines.add(line);
                    }
                  }
                } catch (IOException ignored) {
                  // the process ended
                }
              },
              "budget client " + name);
      reader.setDaemon(true);
      reader.start();
    }

    /** The first line matching {@code pattern}, or {@code null} once the deadline passes. */
    String awaitLine(Pattern pattern, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      while (true) {
        synchronized (lines) {
          for (String line : lines) {
            if (pattern.matcher(line).find()) {
              return line;
            }
          }
        }
        if (System.currentTimeMillis() >= deadline) {
          return null;
        }
        Thread.sleep(100);
      }
    }

    Integer awaitCount(String line, int expected, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      while (true) {
        int count = count(line);
        if (count >= expected) {
          return count;
        }
        if (System.currentTimeMillis() >= deadline) {
          return null;
        }
        Thread.sleep(100);
      }
    }

    int count(String expected) {
      int count = 0;
      synchronized (lines) {
        for (String line : lines) {
          if (line.trim().equals(expected)) {
            count++;
          }
        }
      }
      return count;
    }

    String output() {
      synchronized (lines) {
        return String.join("\n", lines);
      }
    }

    boolean isAlive() {
      return process.isAlive();
    }

    /** The exit code, or -1 if the process is still running after {@code seconds}. */
    int awaitExit(int seconds) throws InterruptedException {
      if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
        return -1;
      }
      // Let the readers drain the last lines before callers print the output.
      Thread.sleep(200);
      return process.exitValue();
    }

    void destroy() throws InterruptedException {
      if (process.isAlive()) {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }
    }
  }
}
