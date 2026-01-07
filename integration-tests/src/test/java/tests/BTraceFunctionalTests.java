/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package tests;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
        2,
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
    if (Files.exists(Paths.get(javaHome, "jre"))) {
      testDynamic(
          "resources.Main",
          "btrace/OnProbeTest.java",
          5,
          new ResultValidator() {
            @Override
            public void validate(String stdout, String stderr, int retcode, String jfrFile) {
              assertFalse(stdout.contains("FAILED"), "Script should not have failed");
              assertTrue(stderr.isEmpty(), "Non-empty stderr");
              assertTrue(stdout.contains("[this, noargs]"));
              assertTrue(stdout.contains("[this, args]"));
            }
          });
    } else {
      System.err.println("XML libraries not available. Skipping @OnProbe tests");
    }
  }

  @Test
  public void testOnTimer() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnTimerTest.java",
        10,
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
  public void testOnTimerArg() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnTimerArgTest.java",
        new String[] {"timer=500"},
        10,
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
        5,
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
          assertTrue(stdout.contains("onexit"));
        });
  }

  @Test
  public void testOnMethod() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/OnMethodTest.java",
        14,
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
            assertTrue(stdout.contains("fieldSet: static field java.lang.String resources.Main#sField"));
            assertTrue(stdout.contains("fieldGet: field java.lang.String resources.Main#field"));
            assertTrue(stdout.contains("fieldGet: static field java.lang.String resources.Main#sField"));
          }
        });
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
      testStartup(
        "resources.Main",
        "traces/TraceAllTest.class",
        null,
        10,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
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
        5,
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
        2,
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
        5,
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
    testDynamic(
        "resources.Main",
        "btrace/OnMethodSubclassTest.java",
        5,
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
    isUnsafe = true;
    testDynamic(
        "resources.Main",
        "btrace/ProbeArgsTest.java",
        new String[] {"arg1", "arg2=val2"},
        5,
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
        5,
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
        5,
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
        30,
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
    // NOTE: This test is flaky in CI due to timing and attach/disconnect nuances.
    // When running in CI (enable via Gradle -PCI), skip it and rely on manual runs locally
    // for correctness until a deterministic strategy is implemented.
    assumeFalse(
        Boolean.getBoolean("CI")
            || "true".equalsIgnoreCase(System.getProperty("CI"))
            || System.getenv("CI") != null,
        "Skipping unattended test in CI: run locally for correctness");

    TestApp testApp = launchTestApp("resources.Main");
    File traceFile = locateTrace("btrace/OnMethodTest.java");

    String pid = String.valueOf(testApp.getPid());
    AtomicBoolean hasError = new AtomicBoolean(false);
    System.out.println("===> btrace -x (unattended)");
    runBTrace(
        new String[] {"-x", pid, traceFile.toString()},
        new ProcessOutputProcessor() {
          @Override
          public boolean onStdout(int lineno, String line) {
            System.out.println("[btrace #" + lineno + "] " + line);
            return lineno < 500;
          }

          @Override
          public boolean onStderr(int lineno, String line) {
            System.err.println("[btrace #" + lineno + "] " + line);
            hasError.set(true);
            return lineno < 10;
          }
        });

    assertFalse(hasError.get(), "btrace -x reported errors");

    // Poll list of probes to discover the probe id reliably
    System.out.println("===> polling btrace -lp for probe id");
    final String[] probeId = new String[1];
    final int[] matchCount = new int[1];
    long start = System.currentTimeMillis();
    while ((System.currentTimeMillis() - start) < 15000) {
      // reset per-iteration state
      matchCount[0] = 0;
      probeId[0] = null;
      runBTrace(
          new String[] {"-lp", pid},
          new ProcessOutputProcessor() {
            @Override
            public boolean onStdout(int lineno, String line) {
              System.out.println("[btrace #" + lineno + "] " + line);
              // Format: "N: <uuid> [<class>]"
              int idx = line.indexOf(':');
              if (idx > -1) {
                String rest = line.substring(idx + 1).trim();
                int sp = rest.indexOf(' ');
                int lb = rest.indexOf('[');
                int rb = rest.indexOf(']');
                if (sp > 0 && lb > sp && rb > lb) {
                  String id = rest.substring(0, sp).trim();
                  String cls = rest.substring(lb + 1, rb).trim();
                  if (cls.endsWith("OnMethodTest")) {
                    matchCount[0]++;
                    if (matchCount[0] == 1) {
                      probeId[0] = id;
                    }
                    if (matchCount[0] > 1) {
                      // No need to read further in this iteration
                      return false;
                    }
                  }
                }
              }
              return lineno < 50;
            }

            @Override
            public boolean onStderr(int lineno, String line) {
              System.err.println("[btrace #" + lineno + "] " + line);
              return false;
            }
          });
      if (matchCount[0] == 1) {
        break; // exactly one match found
      }
      if (matchCount[0] > 1) {
        break; // over-matched; fail via assertion below
      }
      // No matches; wait and retry until timeout
      try {
        Thread.sleep(300);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        try {
          // break outer loop on interrupt
          break;
        } catch (Exception ignore) {}
      }
    }

    // Assert exactly one matching probe is present
    org.junit.jupiter.api.Assertions.assertEquals(1, matchCount[0],
        "expected exactly one OnMethodTest probe listed by -lp");
    assertNotNull(probeId[0], "probe id not found in -lp within timeout");
  }

    @ParameterizedTest(name = "testThreadStart: dynamic={0}")
    @ValueSource(booleans = {true, false})
    public void testThreadStart(boolean dynamic) throws Exception {
        if (dynamic) {
            testDynamic(
                    "resources.ThreadSpawner",
                    "traces/ThreadStart.class",
                    null,
                    10,
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
                    20,
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
        20,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("=== HDR Histogram Metrics Test ==="), "Should contain metrics test header");
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
        String[] versionParts = rtVersion.split("\\+")[0].split("\\.");
        int major = Integer.parseInt(versionParts[0]);
        String updateStr = versionParts.length == 3 ? versionParts[2].replace("0_", "") : "0";
        int idx = updateStr.indexOf('-');
        if (idx > -1) {
            updateStr = updateStr.substring(0, idx);
        }
        int update = Integer.parseInt(updateStr);
        // currently, an attempt to instrument all classes and methods will result in crash in jplis agent for JDK 17
        if (major == 17) {
            return false;
        }
        return true;
    }
}
