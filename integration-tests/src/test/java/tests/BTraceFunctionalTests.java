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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  public static void classSetup() throws Exception {
    setup();
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
      String rtVersion = System.getProperty("java.runtime.version", "");
      String testJavaHome = System.getenv().get("TEST_JAVA_HOME");

      if (testJavaHome != null) {
          Properties releaseProps = new Properties();
          releaseProps.load(
                  Files.newInputStream(new File(testJavaHome + File.separator + "release").toPath()));
          rtVersion = releaseProps.getProperty("JAVA_VERSION").replace("\"", "");
      }
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
    TestApp testApp = launchTestApp("resources.Main");
    File traceFile = locateTrace("btrace/OnMethodTest.java");

    String pid = String.valueOf(testApp.getPid());
    String[] probeId = new String[1];
    AtomicBoolean hasError = new AtomicBoolean(false);
    System.out.println("===> btrace -x");

    // a workaround - for some reason the btrace output is not captured in the client in tests here
    debugBTrace = true;
    runBTrace(
        new String[] {"-x", pid, traceFile.toString()},
        new ProcessOutputProcessor() {
          @Override
          public boolean onStdout(int lineno, String line) {
            if (lineno > 200) {
              return false;
            }
            System.out.println("[btrace #" + lineno + "] " + line);
            if (line.contains("BTrace Probe:")) {
              probeId[0] = line.split(":")[1].trim();
              return false;
            }
            return true;
          }

          @Override
          public boolean onStderr(int lineno, String line) {
            if (lineno > 10) {
              return false;
            }
            System.err.println("[btrace #" + lineno + "] " + line);
            hasError.set(true);
            return true;
          }
        });

    assertFalse(hasError.get());
    assertNotNull(probeId[0]);

    final boolean[] found = new boolean[] {false};
    System.out.println("===> btrace -lp");
    runBTrace(
        new String[] {"-lp", pid},
        new ProcessOutputProcessor() {
          @Override
          public boolean onStdout(int lineno, String line) {
            System.out.println("[btrace #" + lineno + "] " + line);
            if (lineno > 3) {
              return false;
            }
            if (line.contains(probeId[0])) {
              found[0] = true;
              return false;
            }
            return true;
          }

          @Override
          public boolean onStderr(int lineno, String line) {
            System.err.println("[btrace #" + lineno + "] " + line);
            return false;
          }
        });
    assertTrue(found[0]);

    found[0] = false;
    System.out.println("===> btrace -r");
    runBTrace(
        new String[] {"-r", probeId[0], "exit", pid},
        new ProcessOutputProcessor() {
          @Override
          public boolean onStdout(int lineno, String line) {
            System.out.println("[btrace #" + lineno + "] " + line);
            if (lineno > 100) {
              return false;
            }
            if (line.contains(probeId[0])) {
              found[0] = true;
              return false;
            }
            return true;
          }

          @Override
          public boolean onStderr(int lineno, String line) {
            return false;
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
