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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.btrace.client.Client;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.DisconnectCommand;
import io.btrace.core.comm.StatusCommand;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tests.harness.Completion;

/**
 * A set of end-to-end functional tests.
 *
 * <p>The test simulates a user submitting a BTrace script to the target application and asserts
 * that no exceptions are thrown, JVM keeps on running and BTrace generates the anticipated output.
 *
 * @author Jaroslav Bachorik
 */
public class BTraceFunctionalTests extends RuntimeTest {
  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
  }

  @Test
  public void testOSMBean() throws Exception {
    isUnsafe = true;
    testDynamic(
        "resources.Main",
        "btrace/OSMBeanTest.java",
        // n=2, not 1: the client subprocess emits its own "Attaching BTrace to PID" banner line
        // on stdout (Main.java) strictly before the real VirtualMachine.attach() call executes.
        // Waiting for just 1 non-empty line is satisfied by that banner alone, races ahead of the
        // actual attach, and the harness signals the target process to shut down while attach is
        // still in flight -- producing an intermittent "No such process" IOException in the
        // client. n=2 waits past both the "Attaching..." and "Successfully started..." (or
        // equivalent second) banner line first, which is what the original checkLines=2 achieved.
        Completion.untilMatches(Pattern.compile(".+"), 2),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
          }
        });
  }

  @Test
  public void testOnProbe() throws Exception {
    assumeTrue(
        hasJaxbProbeDescriptorSupport(),
        "@OnProbe XML probe descriptors require javax.xml.bind.JAXBException/JAXB support, which is unavailable");
    testDynamic(
        "resources.Main",
        "btrace/OnProbeTest.java",
        Completion.untilContains("[this, noargs]", "[this, args]"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("[this, noargs]"));
            assertTrue(stdout.contains("[this, args]"));
          }
        });
  }

  @Test
  public void testDynamicAttachWithShippedProtocolDefaults() throws Exception {
    setClientProtocolSettings(2, true, false);
    setAgentProtocolSettings(2, true, false);
    testTimerProbe();
  }

  @Test
  public void testDynamicAttachWithV1ClientAgainstV2Agent() throws Exception {
    setClientProtocolSettings(1, false, true);
    setAgentProtocolSettings(2, true, false);
    testTimerProbe();
  }

  @Test
  public void testOnTimer() throws Exception {
    testTimerProbe();
  }

  private void testTimerProbe() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnTimerTest.java",
        Completion.untilContains("vm version", "vm starttime", "timer"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertEquals(0, retcode, "Client should exit successfully");
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("vm version"));
            assertTrue(stdout.contains("vm starttime"));
            assertTrue(stdout.contains("timer"));
          }
        });
  }

  @Test
  public void testOnTimerArg() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnTimerArgTest.java",
        new String[] {"timer=500"},
        Completion.untilContains("vm version", "vm starttime", "timer"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("vm version"));
            assertTrue(stdout.contains("vm starttime"));
            assertTrue(stdout.contains("timer"));
          }
        });
  }

  @Test
  public void testOnExit() throws Exception {
    timeout = 3500;
    testDynamic(
        "resources.Main",
        "btrace/OnExitTest.java",
        Completion.untilContains("onexit"),
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
          assertTrue(stdout.contains("onexit"));
        });
  }

  @Test
  public void testOnMethod() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/OnMethodTest.java",
        Completion.untilContains(
            "[this, noargs]",
            "[this, args]",
            "{xxx}",
            "heap:init",
            "prop: test",
            "fieldSet: field java.lang.String resources.Main#field",
            "fieldSet: static field java.lang.String resources.Main#sField",
            "fieldGet: field java.lang.String resources.Main#field",
            "fieldGet: static field java.lang.String resources.Main#sField"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("[this, noargs]"));
            assertTrue(stdout.contains("[this, args]"));
            assertTrue(stdout.contains("{xxx}"));
            assertTrue(stdout.contains("heap:init"));
            assertTrue(stdout.contains("prop: test"));
            assertTrue(stdout.contains("fieldSet: field java.lang.String resources.Main#field"));
            assertTrue(
                stdout.contains("fieldSet: static field java.lang.String resources.Main#sField"));
            assertTrue(stdout.contains("fieldGet: field java.lang.String resources.Main#field"));
            assertTrue(
                stdout.contains("fieldGet: static field java.lang.String resources.Main#sField"));
          }
        });
  }

  @Test
  public void testOnelinerRuntime() throws Exception {
    dumpOneliner = Boolean.getBoolean("btrace.oneliner.dump");
    dumpVerifierErrors = Boolean.getBoolean("btrace.verifier.dump");
    String oneLiner = "resources.Main::callB @entry { print method, args }";
    testDynamicOneliner(
        "resources.Main",
        oneLiner,
        Completion.untilContains("callB", "Hello World"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("callB"), "Expected oneliner output");
            assertTrue(stdout.contains("Hello World"), "Expected oneliner args output");
          }
        });
  }

  @Test
  public void testExtensionLifecycleClose() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleTest.java",
        new String[] {"extensionCloseTest=true"},
        Completion.untilContains("extension close: btrace-utils"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("extension close: btrace-utils"));
          }
        });
  }

  @Test
  public void locateTracePrefersCurrentBuildOutput() {
    File trace = locateTrace("traces/TraceAllTest.class");
    Path expectedRoot = Paths.get(System.getProperty("project.dir"), "build", "classes");

    assertTrue(
        trace.toPath().normalize().startsWith(expectedRoot.normalize()),
        "Trace lookup must prefer freshly compiled integration test probes");
  }

  @Test
  public void testTraceAll() throws Exception {
    String testJavaHome = System.getenv().get("TEST_JAVA_HOME");
    if (testJavaHome == null) testJavaHome = System.getenv().get("JAVA_TEST_HOME");
    if (testJavaHome == null) {
      testJavaHome = System.getenv("JAVA_HOME");
      if (testJavaHome == null) {
        testJavaHome = System.getProperty("java.home");
      }
    }

    assumeFalse(testJavaHome == null);

    Properties releaseProps = new Properties();
    releaseProps.load(
        Files.newInputStream(new File(testJavaHome + File.separator + "release").toPath()));
    String rtVersion = releaseProps.getProperty("JAVA_VERSION").replace("\"", "");
    if (!isVersionSafeForTraceAll(rtVersion)) {
      System.err.println("Skipping test for JDK " + rtVersion);
      return;
    }
    // The Java 26+ path is the branch's target: keep the full startup retransformation stress
    // there. Older target JDKs still validate startup trace-all instrumentation for newly loaded
    // application classes, but avoid retransformation of every class already loaded by the VM.
    startupRetransform = isVersionAtLeast(rtVersion, 26);
    testStartup(
        "resources.Main",
        "traces/TraceAllTest.class",
        null,
        // TraceAllTest's @OnTimer handler only prints once it has observed at least one traced
        // invocation, so "[invocations=" is a real content marker (unlike testOSMBean's client
        // banner problem) rather than framework bootstrap noise -- wait for it directly instead
        // of guessing at a line count.
        Completion.untilContains("[invocations="),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("[invocations="));
          }
        });
  }

  @Test
  public void testOnMethodLevel() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnMethodLevelTest.java",
        new String[] {"level=200"},
        Completion.untilContains("[this, noargs]", "[this, args]", "{xxx}"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("[this, noargs]"));
            assertTrue(stdout.contains("[this, args]"));
            assertTrue(stdout.contains("{xxx}"));
          }
        });
  }

  @Test
  public void testOnMethodTrackRetransform() throws Exception {
    trackRetransforms = true;
    testDynamic(
        "resources.Main",
        "btrace/OnMethodTest.java",
        Completion.untilContains("Going to retransform class"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("Going to retransform class"));
          }
        });
  }

  @Test
  public void testOnMethodReturn() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnMethodReturnTest.java",
        Completion.untilContains("[this, anytype(void)]", "[this, void]", "[this, 2]"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("[this, anytype(void)]"));
            assertTrue(stdout.contains("[this, void]"));
            assertTrue(stdout.contains("[this, 2]"));
          }
        });
  }

  @Test
  public void testOnMethodSubclass() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/OnMethodSubclassTest.java",
        Completion.untilContains("print:class resources.Main"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("print:class resources.Main"));
          }
        });
  }

  @Test
  public void testProbeArgs() throws Exception {
    //    debugBTrace = true;
    //    debugTestApp = true;
    isUnsafe = true;
    testDynamic(
        "resources.Main",
        "btrace/ProbeArgsTest.java",
        new String[] {"arg1", "arg2=val2"},
        Completion.untilContains("arg#=2", "arg1=", "arg2=val2"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("arg#=2"));
            assertTrue(stdout.contains("arg1="));
            assertTrue(stdout.contains("arg2=val2"));
            assertFalse(stdout.contains("matching probe"));
          }
        });
  }

  @Test
  public void testPerfCounter() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/PerfCounterTest.java",
        Completion.untilContains("matching probe"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("matching probe"));
          }
        });
  }

  @Test
  public void testReflection() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/issues/BTRACE400.java",
        Completion.untilContains(
            "private java.lang.String resources.Main.id", "class resources.Main"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("private java.lang.String resources.Main.id"));
            assertTrue(stdout.contains("class resources.Main"));
          }
        });
  }

  @Test
  public void testJfr() throws Exception {
    String rtVersion = System.getProperty("java.runtime.version", "");
    String testJavaHome = System.getenv().get("TEST_JAVA_HOME");
    if (testJavaHome == null) testJavaHome = System.getenv().get("JAVA_TEST_HOME");
    if (testJavaHome != null) {
      Properties releaseProps = new Properties();
      releaseProps.load(
          Files.newInputStream(new File(testJavaHome + File.separator + "release").toPath()));
      rtVersion = releaseProps.getProperty("JAVA_VERSION").replace("\"", "");
    }
    if (!isVersionSafeForJfr(rtVersion)) {
      // skip the test for 8.0.* because of missing support
      // skip all non-LTS versions (except the last one)
      // skip the test for JDK 11 since the latest version 11.0.9 and newer ends in SISGSEGV
      System.err.println("Skipping test for JDK " + rtVersion);
      return;
    }
    testWithJfr(
        "resources.Main",
        "btrace/JfrTest.java",
        Completion.untilContains("Main.callA"),
        Completion.lines(30),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertNotNull(jfrFile);
            try {
              RecordingFile f = new RecordingFile(Paths.get(jfrFile));
              boolean hasPeriodicType = false,
                  hasPeriodicValue = false,
                  hasCustomType = false,
                  hasCustomValue = false;
              for (EventType et : f.readEventTypes()) {
                if (et.getName().equals("periodic")) {
                  hasPeriodicType = true;
                } else if (et.getName().equals("custom")) {
                  hasCustomType = true;
                }
                if (hasPeriodicType && hasCustomType) {
                  while (f.hasMoreEvents()) {
                    RecordedEvent e = f.readEvent();
                    if (e.getEventType().getName().equals("periodic")) {
                      hasPeriodicValue = true;
                    } else if (e.getEventType().getName().equals("custom")) {
                      hasCustomValue = true;
                    }
                    if (hasPeriodicValue && hasCustomValue) {
                      return;
                    }
                  }
                  break;
                }
              }
              fail(
                  "periodic type ok: "
                      + hasPeriodicType
                      + ", periodic value ok: "
                      + hasPeriodicValue
                      + ", custom type ok: "
                      + hasCustomType
                      + ", custom value ok: "
                      + hasCustomValue);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  @Test
  public void testOnMethodUnattended() throws Exception {
    TestApp testApp = launchTestApp("resources.Main");
    File traceFile = locateTrace("btrace/OnMethodTest.java");

    String pid = String.valueOf(testApp.getPid());
    String host = "localhost";
    Client client = createClientForTests(traceFile.getParentFile().getAbsolutePath());
    client.attach(pid, null, getEventsClassPath());
    byte[] code = client.compile(traceFile.getAbsolutePath(), getEventsClassPath());
    assertNotNull(code, "BTrace compilation failed");

    CompletableFuture<String> probeIdFuture = new CompletableFuture<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> submitFuture =
        executor.submit(
            () -> {
              try {
                client.submit(
                    host,
                    traceFile.getName(),
                    code,
                    new String[0],
                    cmd -> {
                      if (cmd.getType() == Command.STATUS) {
                        StatusCommand status = (StatusCommand) cmd;
                        if (status.getFlag() == StatusCommand.STATUS_FLAG && status.isSuccess()) {
                          try {
                            client.sendDisconnect();
                          } catch (IOException e) {
                            probeIdFuture.completeExceptionally(e);
                          }
                        } else if (!status.isSuccess()) {
                          probeIdFuture.completeExceptionally(
                              new IllegalStateException("Probe startup failed"));
                        }
                      } else if (cmd.getType() == Command.DISCONNECT) {
                        DisconnectCommand disconnect = (DisconnectCommand) cmd;
                        probeIdFuture.complete(disconnect.getProbeId());
                      }
                    });
              } catch (IOException e) {
                if (!probeIdFuture.isDone()) {
                  probeIdFuture.completeExceptionally(e);
                }
              }
            });

    String probeId = probeIdFuture.get(15, TimeUnit.SECONDS);
    client.close();
    try {
      submitFuture.get(5, TimeUnit.SECONDS);
    } catch (Exception ignore) {
      // best effort; disconnect closes the socket and may surface as a submit error
    } finally {
      executor.shutdownNow();
    }

    List<String> probes = listProbesWithProtocol(host);
    long matches =
        probes.stream().filter(p -> extractProbeClassName(p).endsWith("OnMethodTest")).count();
    assertEquals(1, matches, "expected exactly one OnMethodTest probe listed by -lp");
    assertTrue(
        probes.stream().anyMatch(p -> p.startsWith(probeId + " ")),
        "probe id not present in -lp output");
  }

  private static String extractProbeClassName(String probeEntry) {
    int lb = probeEntry.indexOf('[');
    int rb = probeEntry.indexOf(']');
    if (lb > -1 && rb > lb) {
      return probeEntry.substring(lb + 1, rb).trim();
    }
    return "";
  }

  @ParameterizedTest(name = "testThreadStart: dynamic={0}")
  @ValueSource(booleans = {true, false})
  public void testThreadStart(boolean dynamic) throws Exception {
    if (dynamic) {
      testDynamic(
          "resources.ThreadSpawner",
          "traces/ThreadStart.class",
          null,
          Completion.untilContains("starting testThread"),
          new ResultValidator() {
            @Override
            public void validate(String stdout, String stderr, int retcode, String jfrFile) {
              assertFalse(stdout.contains("FAILED"), "Script should not have failed");
              assertTrue(stderr.isEmpty(), "Non-empty stderr");
              assertTrue(stdout.contains("starting testThread"));
            }
          });
    } else {
      testStartup(
          "resources.ThreadSpawner",
          "traces/ThreadStart.class",
          null,
          // Same marker as the dynamic=true branch above.
          Completion.untilContains("starting testThread"),
          new ResultValidator() {
            @Override
            public void validate(String stdout, String stderr, int retcode, String jfrFile) {
              assertFalse(stdout.contains("FAILED"), "Script should not have failed");
              assertTrue(stderr.isEmpty(), "Non-empty stderr");
              assertTrue(stdout.contains("starting testThread"));
            }
          });
    }
  }

  @Test
  @DisplayName("Test HDR Histogram Metrics Integration")
  public void testMetrics() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/MetricsTest.java",
        Completion.untilContains(
            "=== HDR Histogram Metrics Test ===",
            "=== Metrics Report ===",
            "Count:",
            "Mean:",
            "P50:",
            "P95:",
            "P99:"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(
                stdout.contains("=== HDR Histogram Metrics Test ==="),
                "Should contain metrics test header");
            assertTrue(stdout.contains("=== Metrics Report ==="), "Should contain metrics report");
            assertTrue(stdout.contains("Count:"), "Should contain count");
            assertTrue(stdout.contains("Mean:"), "Should contain mean");
            assertTrue(stdout.contains("P50:"), "Should contain P50");
            assertTrue(stdout.contains("P95:"), "Should contain P95");
            assertTrue(stdout.contains("P99:"), "Should contain P99");
          }
        });
  }

  private static boolean isVersionSafeForJfr(String rtVersion) {
    System.out.println("===> version: " + rtVersion);
    String[] versionParts = rtVersion.split("\\+")[0].split("\\.");
    int major = Integer.parseInt(versionParts[0]);
    String updateStr = versionParts.length == 3 ? versionParts[2].replace("0_", "") : "0";
    int idx = updateStr.indexOf('-');
    if (idx > -1) {
      updateStr = updateStr.substring(0, idx);
    }
    int update = Integer.parseInt(updateStr);
    if (major == 8) {
      // before 8u272 there was no JFR support
      return update > 272;
    } else if (major > 9) { // in JDK 9 the dynamic JFR events are missing
      if (major == 11) {
        // 11.0.9 and 11.0.10 are containing a bug causing the JFR initialization from BTrace to go
        // into infinite loop
        return update < 9 || update > 11;
      }
      return true;
    }
    return false;
  }

  private static boolean isVersionSafeForTraceAll(String rtVersion) {
    System.out.println("===> version: " + rtVersion);
    int major = parseJavaMajor(rtVersion);
    // currently, an attempt to instrument all classes and methods will result in crash in jplis
    // agent for JDK 17
    if (major == 17) {
      return false;
    }
    return true;
  }

  private static boolean isVersionAtLeast(String rtVersion, int expectedMajor) {
    return parseJavaMajor(rtVersion) >= expectedMajor;
  }

  private static int parseJavaMajor(String rtVersion) {
    String normalized = rtVersion.replace("\"", "");
    if (normalized.startsWith("1.")) {
      normalized = normalized.substring(2);
    }

    StringBuilder major = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);
      if (!Character.isDigit(ch)) {
        break;
      }
      major.append(ch);
    }
    return major.length() == 0 ? 0 : Integer.parseInt(major.toString());
  }

  @Test
  public void testOnelinerMethodEntry() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "resources.Main::callA @entry { print method }",
        Completion.untilContains("callA"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("callA"), "Method entry not captured");
          }
        });
  }

  @Test
  public void testOnelinerWithArguments() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "resources.Main::callB @entry { print args }",
        Completion.untilContains("[1, Hello World]"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("[1, Hello World]"), "Arguments not captured correctly");
          }
        });
  }

  @Test
  public void testOnelinerWithReturn() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "resources.Main::callB @return { print method, duration }",
        Completion.untilContains("callB"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("callB"), "Return method name not captured");
          }
        });
  }

  @Test
  public void testOnelinerWithRegexClassMatch() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "/resources\\..*Main/::callA @entry { print method }",
        Completion.untilContains("callA"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("callA"), "Regex class matching not working");
          }
        });
  }

  @Test
  public void testOnelinerStack() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "resources.Main::callB @entry { stack }",
        Completion.untilContains("resources.Main.callA"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(
                stdout.contains("resources.Main.callA") || stdout.contains("resources.Main"),
                "Stack trace not captured");
          }
        });
  }

  @Test
  public void testOnelinerCompilationError() throws Exception {
    testDynamicOneliner(
        "resources.Main",
        "resources.Main::callB @invalid { print }",
        new Completion() {
          @Override
          public boolean onStdout(String line) {
            return line.toLowerCase(Locale.ROOT).contains("error");
          }

          @Override
          public boolean onStderr(String line) {
            return true;
          }

          @Override
          public String describe() {
            return "a compile error on stdout or any stderr line";
          }
        },
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            // Compilation errors should be reported
            assertTrue(
                !stderr.isEmpty() || stdout.contains("error") || stdout.contains("Error"),
                "Expected compilation error not reported");
          }
        });
  }

  @Test
  @DisplayName("Flat DSL ops work without BTraceUtils import")
  public void flatDslOpsWork() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/FlatDslTest.java",
        Completion.untilContains("flat-dsl:"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("flat-dsl:"), "Expected flat-dsl output not found");
          }
        });
  }
}
