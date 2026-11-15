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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MainTest {
  @Test
  void commaSeparatedGrantListSurvivesArgumentSplitting() {
    ArgsMap args = Main.parseAgentArgs("script=Foo.class,grant=NETWORK,THREADS,grantAll=false");

    assertEquals("Foo.class", args.get("script"));
    assertEquals("NETWORK,THREADS", args.get("grant"));
    assertEquals("false", args.get("grantAll"));
    assertNull(args.get("THREADS"));
  }

  @Test
  void everyListValuedKeyContinuesAcrossCommas() {
    ArgsMap args =
        Main.parseAgentArgs(
            "deny=FILE_WRITE,PROCESS,allowExtensions=a.b,c.d,denyExtensions=e,f,"
                + "probes=DriverTracer,ExecutorTracer,debug=true");

    assertEquals("FILE_WRITE,PROCESS", args.get("deny"));
    assertEquals("a.b,c.d", args.get("allowExtensions"));
    assertEquals("e,f", args.get("denyExtensions"));
    assertEquals("DriverTracer,ExecutorTracer", args.get("probes"));
    assertNull(args.get("ExecutorTracer"));
    assertEquals("true", args.get("debug"));
  }

  @Test
  void bareTokensStayKeysOutsideLists() {
    ArgsMap plain = Main.parseAgentArgs("debug=true,help");
    assertEquals("", plain.get("help"));

    ArgsMap afterList = Main.parseAgentArgs("grant=NETWORK,help");
    assertEquals("NETWORK", afterList.get("grant"));
    assertEquals("", afterList.get("help"));

    ArgsMap afterScalar = Main.parseAgentArgs("debug=true,THREADS");
    assertEquals("", afterScalar.get("THREADS"));
    assertNull(afterScalar.get("grant"));
  }

  @Test
  void nullAndEmptyArgumentStringsParseWithoutLists() {
    assertNull(Main.parseAgentArgs(null).get("grant"));
    assertNull(Main.parseAgentArgs("").get("grant"));
  }

  @Test
  void locateScriptsEmpty() {
    ArgsMap argsMap = new ArgsMap();
    List<String> scripts = Main.locateScripts(argsMap);

    assertTrue(scripts.isEmpty());
  }

  @Test
  void locateScriptsSingle() {
    ArgsMap argsMap = new ArgsMap(new String[] {"script=script1"});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(1, scripts.size());
  }

  @Test
  void locateScriptsMulti() {
    ArgsMap argsMap = new ArgsMap(new String[] {"script=script1:script2"});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(2, scripts.size());
  }

  @Test
  void locateScriptsDir() throws Exception {
    Path dir = Files.createTempDirectory("test-");
    Path script = Files.createTempFile(dir, "script-", ".btrace");
    ArgsMap argsMap = new ArgsMap(new String[] {"scriptdir=" + dir.toString()});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(1, scripts.size());
  }

  @Test
  void parseFlagAcceptsOnlyBooleanSpellingsAndTheBareFlag() {
    assertEquals(Boolean.TRUE, Main.parseFlag(""));
    assertEquals(Boolean.TRUE, Main.parseFlag("true"));
    assertEquals(Boolean.TRUE, Main.parseFlag("TRUE"));
    assertEquals(Boolean.FALSE, Main.parseFlag("false"));
    assertEquals(Boolean.FALSE, Main.parseFlag("False"));
    for (String invalid : new String[] {"no", "yes", "0", "1", "off", "on", " true"}) {
      assertNull(Main.parseFlag(invalid), invalid);
    }
  }

  @Test
  void noServerArgumentDisablesEndpoint() {
    assertFalse(Main.shouldStartServer(new ArgsMap(new String[] {"noServer=true"})));
  }

  @Test
  void startupScriptDefaultsToNoServer() {
    assertFalse(Main.shouldStartServer(new ArgsMap(new String[] {"script=probe.class"})));
  }

  @Test
  void explicitServerOverridesStartupScriptDefault() {
    assertTrue(
        Main.shouldStartServer(new ArgsMap(new String[] {"script=probe.class", "noServer=false"})));
  }

  /**
   * Regression for #932: the "BTrace Server Shutdown" hook must make progress while agent
   * initialization is still in flight. It used to share the {@code Main.class} monitor with the
   * whole of {@code main()}, so a target exiting during init hung in JVM exit.
   */
  @Test
  void serverShutdownDoesNotWaitForInFlightInitialization() throws Exception {
    CountDownLatch initLockHeld = new CountDownLatch(1);
    CountDownLatch releaseInit = new CountDownLatch(1);
    Thread init =
        new Thread(
            () ->
                Main.runUnderInitLockForTesting(
                    () -> {
                      initLockHeld.countDown();
                      try {
                        releaseInit.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    }),
            "simulated-agent-init");
    init.setDaemon(true);
    init.start();
    Thread shutdown = new Thread(Main::shutdownServer, "simulated-shutdown-hook");
    shutdown.setDaemon(true);
    try {
      assertTrue(initLockHeld.await(10, TimeUnit.SECONDS), "init lock was never taken");
      shutdown.start();
      shutdown.join(TimeUnit.SECONDS.toMillis(10));
      assertFalse(shutdown.isAlive(), "shutdownServer() blocked on the agent init lock");
    } finally {
      releaseInit.countDown();
      init.join(TimeUnit.SECONDS.toMillis(10));
      shutdown.join(TimeUnit.SECONDS.toMillis(10));
    }
  }

  @Test
  void dollarBudgetPropertyArgumentIsRejected() {
    String name = "io.btrace.probeMaxRate";
    String before = System.getProperty(name);
    List<String> reported = new ArrayList<>();
    try {
      System.clearProperty(name);
      Main.applyDollarProperty("$" + name, "999999", reported::add);
      assertNull(System.getProperty(name));
      assertEquals(1, reported.size());
      assertTrue(reported.get(0).contains("$" + name), reported.get(0));

      reported.clear();
      Main.applyDollarProperty("$io.btrace.probeTtl", "1", reported::add);
      assertNull(System.getProperty("io.btrace.probeTtl"));
      assertEquals(1, reported.size());
    } finally {
      if (before == null) {
        System.clearProperty(name);
      } else {
        System.setProperty(name, before);
      }
    }
  }

  @Test
  void otherDollarArgumentsStillSetProperties() {
    String name = "some.other.btrace.test.property";
    List<String> reported = new ArrayList<>();
    try {
      Main.applyDollarProperty("$" + name, "1", reported::add);
      assertEquals("1", System.getProperty(name));
      assertTrue(reported.isEmpty());
    } finally {
      System.clearProperty(name);
    }
  }

  @Test
  void startupProbesAreExemptFromBudgetsOnlyWhenLoadedAtLaunch() {
    assertTrue(Main.startupProbesExempt("premain"), "a launch-time startup probe must be exempt");
    assertFalse(
        Main.startupProbesExempt("agentmain"), "an attach-time startup probe must be budgeted");
    assertFalse(
        Main.startupProbesExempt("unknown"), "an unknown load mode must not exempt a probe");
  }

  @Test
  void bundledProbeRejectedOnlyBecauseTrustWasRefusedIsSkipped() {
    assertTrue(
        Main.skipsUnverifiedBundledProbe(Main.ScriptLoad.UNVERIFIED, true, true),
        "a bundled probe that failed verification after trusted=true was ignored must be skipped");
  }

  @Test
  void otherBundledProbeFailuresStayFatal() {
    // No budget refusing trust (no budget, or trusted=true granted at launch).
    assertFalse(Main.skipsUnverifiedBundledProbe(Main.ScriptLoad.UNVERIFIED, false, true));
    // Trust refused, but nobody asked for it: the probe is simply invalid.
    assertFalse(Main.skipsUnverifiedBundledProbe(Main.ScriptLoad.UNVERIFIED, true, false));
    // Missing or broken for another reason.
    assertFalse(Main.skipsUnverifiedBundledProbe(Main.ScriptLoad.FAILED, true, true));
    assertFalse(Main.skipsUnverifiedBundledProbe(Main.ScriptLoad.LOADED, true, true));
  }

  @Test
  void invalidOperatorBudgetStopsAttachTimeProbesOnly() {
    assertFalse(
        Main.loadsStartupProbes(false, "agentmain"),
        "an attacher's probes loaded with an invalid operator budget");
    assertFalse(
        Main.loadsStartupProbes(false, "unknown"),
        "an unknown load mode must not load probes with an invalid operator budget");
    assertTrue(
        Main.loadsStartupProbes(false, "premain"),
        "the operator's own startup scripts must still run with an invalid value");
    assertTrue(Main.loadsStartupProbes(true, "agentmain"), "a valid budget must not stop probes");
    assertTrue(Main.loadsStartupProbes(true, "premain"), "a valid budget must not stop probes");
  }

  @Test
  void refusedTrustIsClearedAtTheSourceSoNoClientInheritsIt() {
    SharedSettings agentSettings = new SharedSettings();
    agentSettings.setTrusted(true);
    assertTrue(
        Main.newClientSettings(agentSettings).isTrusted(),
        "without a budget a client must inherit trusted mode as before");
    try {
      assertTrue(Main.refuseTrustedMode(agentSettings), "the trusted request was not reported");
      assertTrue(Client.isTrustedModeRefused());
      assertFalse(agentSettings.isTrusted(), "the agent-wide settings still say trusted");
      assertFalse(
          Main.newClientSettings(agentSettings).isTrusted(),
          "a client inherited trusted mode from attach arguments under a budget");
      assertFalse(Main.refuseTrustedMode(new SharedSettings()), "no trusted request to report");
    } finally {
      Client.refuseTrustedMode(false);
    }
  }
}
