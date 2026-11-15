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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.Args;
import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import io.btrace.runtime.BTraceRuntimeAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
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
  void trustedSystemPropertyWithoutAgentArgumentIsReported() {
    String warning = Main.ineffectiveTrustedPropertyWarning("true", false, false);

    assertTrue(warning.contains("btrace.trusted=true"), warning);
    assertTrue(warning.contains("has no effect"), warning);
    assertTrue(warning.contains("trusted=true agent argument"), warning);
  }

  @Test
  void trustedSystemPropertyIsNotReportedWhenUnsetOrTrustedAnyway() {
    assertNull(Main.ineffectiveTrustedPropertyWarning(null, false, false));
    assertNull(Main.ineffectiveTrustedPropertyWarning("true", true, false));
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
  void noClientInheritsTrustFromAttachArgumentsUnderABudget() {
    SharedSettings agentSettings = new SharedSettings();
    try {
      Main.applyTrustedArgument(agentSettings, true, false);
      assertTrue(
          Main.newClientSettings(agentSettings).isTrusted(),
          "without a budget a client must inherit trusted mode as before");

      SharedSettings budgeted = new SharedSettings();
      Main.applyTrustedArgument(budgeted, true, true);
      assertFalse(budgeted.isTrusted(), "the agent-wide settings say trusted");
      assertFalse(
          Main.newClientSettings(budgeted).isTrusted(),
          "a client inherited trusted mode from attach arguments under a budget");
      assertTrue(Main.isTrustRequestedAndRefused(), "the trusted request was not recorded");
    } finally {
      Main.resetTrustRefusalForTesting();
    }
  }

  @Test
  void attachTimeTrustedArgumentIsNotAppliedUnderAnOperatorBudget() {
    SharedSettings agentSettings = new SharedSettings();
    try {
      Main.applyTrustedArgument(agentSettings, true, true);
      assertFalse(
          agentSettings.isTrusted(),
          "trusted mode was on while the remaining agent arguments were processed");
      assertTrue(Main.isTrustRequestedAndRefused(), "the refused request was not recorded");
    } finally {
      Main.resetTrustRefusalForTesting();
    }
  }

  @Test
  void trustedArgumentIsAppliedWhenNotBarred() {
    SharedSettings agentSettings = new SharedSettings();
    try {
      Main.applyTrustedArgument(agentSettings, true, false);
      assertTrue(agentSettings.isTrusted());
      assertFalse(Main.isTrustRequestedAndRefused());
      // The operator's launch-time grant: an attacher under a budget cannot take it away.
      Main.applyTrustedArgument(agentSettings, false, true);
      assertTrue(agentSettings.isTrusted(), "an attach-time trusted=false revoked the grant");
      assertFalse(Main.isTrustRequestedAndRefused(), "trusted=false is not a refused request");
      Main.applyTrustedArgument(agentSettings, false, false);
      assertFalse(agentSettings.isTrusted());
    } finally {
      Main.resetTrustRefusalForTesting();
    }
  }

  @Test
  void trustedHintDoesNotRecommendTheClientFlagUnderABudget() {
    String plain = Main.ineffectiveTrustedPropertyWarning("true", false, false);
    assertTrue(plain.contains("or the client's -u flag"), plain);
    String budgeted = Main.ineffectiveTrustedPropertyWarning("true", false, true);
    assertTrue(budgeted.contains("at JVM launch"), budgeted);
    assertTrue(budgeted.contains("the client's -u flag is refused"), budgeted);
    assertNull(Main.ineffectiveTrustedPropertyWarning("true", true, true));
  }

  @Test
  void onlyOneAgentInstanceHoldsTheJvmUntilItReleasesIt() {
    UnaryOperator<String> first = args -> "first";
    UnaryOperator<String> second = args -> "second";
    assertTrue(BTraceRuntimeAccess.claimAgent(first), "the first load was refused");
    try {
      assertFalse(BTraceRuntimeAccess.claimAgent(second), "a second load was accepted");
      assertSame(first, BTraceRuntimeAccess.agentControl());
      // Only the holder can give the claim back.
      BTraceRuntimeAccess.releaseAgent(second);
      assertSame(first, BTraceRuntimeAccess.agentControl());
    } finally {
      BTraceRuntimeAccess.releaseAgent(first);
    }
    assertNull(BTraceRuntimeAccess.agentControl());
    assertTrue(BTraceRuntimeAccess.claimAgent(second), "a released JVM could not be claimed");
    BTraceRuntimeAccess.releaseAgent(second);
  }

  @Test
  void laterLoadGetsNoServerFromAnAgentThatDidNotInitialize() {
    // This test JVM never ran agent initialization.
    String outcome = Main.startServerOnAttach("port=0");
    assertTrue(outcome.contains("its command server was not started"), outcome);
    // It installed nothing either, so the JVM is free for another load.
    assertTrue(outcome.contains("failed to initialize; load the agent again"), outcome);
  }

  @Test
  void laterLoadIsToldToRestartTheJvmAfterAnAgentThatFailedWithItsHooksInstalled() {
    // This test JVM never finished agent initialization; say it got as far as its runtime hooks.
    Main.setRuntimeHooksInstalledForTesting(true);
    try {
      String outcome = Main.startServerOnAttach("port=0");
      assertTrue(outcome.contains("its command server was not started"), outcome);
      assertTrue(
          outcome.contains("failed to initialize; restart the JVM to use BTrace in it"), outcome);
    } finally {
      Main.setRuntimeHooksInstalledForTesting(false);
    }
  }

  @Test
  void loadClaimsTheJvmAgainWhenTheHolderGaveItBackInBetween() {
    UnaryOperator<String> failedHolder = args -> "failed holder";
    UnaryOperator<String> own = args -> "own";
    AtomicInteger lookups = new AtomicInteger();
    assertTrue(BTraceRuntimeAccess.claimAgent(failedHolder));
    try {
      // The claim is refused, and by the time the holder is looked up it has given the JVM back.
      String outcome =
          Main.claimOrHandOver(
              "port=0",
              own,
              () -> {
                lookups.incrementAndGet();
                BTraceRuntimeAccess.releaseAgent(failedHolder);
                return BTraceRuntimeAccess.agentControl();
              });

      assertNull(outcome, "the load was handed over instead of claiming the JVM");
      assertEquals(1, lookups.get());
      assertSame(own, BTraceRuntimeAccess.agentControl());
    } finally {
      BTraceRuntimeAccess.releaseAgent(failedHolder);
      BTraceRuntimeAccess.releaseAgent(own);
    }
  }

  @Test
  void loadIsHandedOverToTheAgentHoldingTheJvm() {
    List<String> seen = new ArrayList<>();
    UnaryOperator<String> holder =
        args -> {
          seen.add(args);
          return "handled by the holder";
        };
    UnaryOperator<String> own = args -> "own";
    assertTrue(BTraceRuntimeAccess.claimAgent(holder));
    try {
      assertEquals(
          "handled by the holder",
          Main.claimOrHandOver("port=4242", own, BTraceRuntimeAccess::agentControl));
      assertEquals(1, seen.size());
      assertEquals("port=4242", seen.get(0));
      assertSame(holder, BTraceRuntimeAccess.agentControl());
    } finally {
      BTraceRuntimeAccess.releaseAgent(holder);
    }
  }

  @Test
  void holderThatThrowsIsReportedAndKeepsTheJvm() {
    UnaryOperator<String> holder =
        args -> {
          throw new IllegalStateException("holder broke");
        };
    assertTrue(BTraceRuntimeAccess.claimAgent(holder));
    try {
      String outcome =
          Main.claimOrHandOver("port=0", args -> "own", BTraceRuntimeAccess::agentControl);
      assertTrue(outcome.contains("its command server was not started"), outcome);
      assertTrue(outcome.contains("holder broke"), outcome);
      assertSame(holder, BTraceRuntimeAccess.agentControl());
    } finally {
      BTraceRuntimeAccess.releaseAgent(holder);
    }
  }

  @Test
  void holderThatGivesNoAnswerDoesNotLetTheLoadInitialize() {
    UnaryOperator<String> holder = args -> null;
    assertTrue(BTraceRuntimeAccess.claimAgent(holder));
    try {
      String outcome =
          Main.claimOrHandOver("port=0", args -> "own", BTraceRuntimeAccess::agentControl);
      assertNotNull(outcome, "the load was told to initialize a second agent");
      assertTrue(outcome.contains("its command server was not started"), outcome);
      assertSame(holder, BTraceRuntimeAccess.agentControl());
    } finally {
      BTraceRuntimeAccess.releaseAgent(holder);
    }
  }

  @Test
  void endpointIsClosedAndUnpublishedWhenItsServerThreadCannotStart() throws Exception {
    Thread unstartable =
        new Thread() {
          @Override
          public synchronized void start() {
            throw new OutOfMemoryError("unable to create native thread");
          }
        };
    String wireBefore = System.getProperty("btrace.wireio");
    ControlServer endpoint = ControlServer.open(Main.parseAgentArgs("port=0"), true);
    try {
      Path tokenFile = Paths.get(System.getProperty(ControlServer.TOKEN_FILE_PROPERTY));
      assertTrue(Files.exists(tokenFile), "no token file was generated");

      String outcome = Main.serveOnAttach(endpoint, unstartable);

      assertTrue(outcome.contains("its command server was not started"), outcome);
      assertTrue(outcome.contains("unable to create native thread"), outcome);
      assertTrue(endpoint.isClosed(), "the endpoint was left open");
      assertNull(System.getProperty(ControlServer.PORT_PROPERTY));
      assertNull(System.getProperty(ControlServer.AUTH_REQUIRED_PROPERTY));
      assertNull(System.getProperty(ControlServer.TOKEN_FILE_PROPERTY));
      assertFalse(Files.exists(tokenFile), "the generated token file was left behind");
      // Otherwise every later load would be told the agent has a server already.
      assertFalse(Main.hasCommandServerForTesting(), "the agent still holds the closed endpoint");
    } finally {
      Main.shutdownServer();
      endpoint.close();
      if (wireBefore == null) {
        System.clearProperty("btrace.wireio");
      } else {
        System.setProperty("btrace.wireio", wireBefore);
      }
    }
  }

  @Test
  void serverStartedForALaterLoadTakesOnlyItsEndpointFromThatLoad() {
    ArgsMap laterLoad =
        Main.parseAgentArgs(
            "port=4242,bindAddress=::1,authTokenFile=/attacher/token,trusted=true,noServer=true");
    ArgsMap ownLoad = Main.parseAgentArgs("noServer=true,authTokenFile=/operator/token,port=1");

    ArgsMap endpoint = Main.attachEndpointArgs(laterLoad, ownLoad);

    assertEquals("4242", endpoint.get(Args.PORT));
    assertEquals("::1", endpoint.get(Args.BIND_ADDRESS));
    assertEquals("/operator/token", endpoint.get(Args.AUTH_TOKEN_FILE));
    assertEquals(3, endpoint.size());
  }

  @Test
  void serverStartedForALaterLoadGetsNoTokenFileFromThatLoad() {
    ArgsMap endpoint =
        Main.attachEndpointArgs(
            Main.parseAgentArgs("port=0,authTokenFile=/attacher/token"),
            Main.parseAgentArgs("noServer=true"));

    assertNull(endpoint.get(Args.AUTH_TOKEN_FILE));
    assertEquals("0", endpoint.get(Args.PORT));
  }
}
