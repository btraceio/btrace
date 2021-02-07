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

package org.openjdk.btrace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
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
    test(
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
      test(
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
    test(
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
    test(
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
    timeout = 1500;
    test(
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
    test(
        "resources.Main",
        "btrace/OnMethodTest.java",
        10,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr");
            assertTrue(stdout.contains("[this, noargs]"));
            assertTrue(stdout.contains("[this, args]"));
            assertTrue(stdout.contains("{xxx}"));
            assertTrue(stdout.contains("heap:init"));
          }
        });
  }

  @Test
  public void testOnMethodLevel() throws Exception {
    test(
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
    test(
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
    test(
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
    test(
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
    test(
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
    test(
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
    test(
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
      releaseProps.load(new FileInputStream(new File(testJavaHome + File.separator + "release")));
      rtVersion = releaseProps.getProperty("JAVA_VERSION").replace("\"", "");
    }
    if (!rtVersion.startsWith("15.")) {
      // skip the test for 8.0.* because of missing support
      // skip all non-LTS versions (except the last one)
      // skip the test for JDK 11 since the latest version 11.0.9 and newer ends in SISGSEGV
      System.err.println("Skipping test for JDK " + rtVersion);
      return;
    }
    testWithJfr(
        "resources.Main",
        "btrace/JfrTest.java",
        10,
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
  public void testExtension() throws Exception {
      debugTestApp = true;
      debugBTrace = true;
      test(
            "resources.Main",
            "btrace/extensions/SimpleExtensionTest.java",
            10,
            (stdout, stderr, retcode, jfrFile) -> {
                assertFalse("Script should not have failed", stdout.contains("FAILED"));
                assertTrue("Non-empty stderr", stderr.isEmpty());
                assertTrue(stdout.contains("[this, noargs]"));
                assertTrue(stdout.contains("[this, args]"));
                assertTrue(stdout.contains("{xxx}"));
                assertTrue(stdout.contains("heap:init"));
            });
  }
}
