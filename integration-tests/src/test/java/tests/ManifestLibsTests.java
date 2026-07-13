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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tests.harness.Completion;

public class ManifestLibsTests extends RuntimeTest {
  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    // Toggle manifest-libs feature and skip libs for these tests
    System.setProperty("btrace.feature.manifestLibs", "true");
    System.setProperty("btrace.test.skipLibs", "true");
    // Use a distinct port to avoid contention with other test classes
    // that may leave port 2020 briefly occupied after teardown
    System.setProperty("btrace.port", "2022");
  }

  @AfterEach
  public void cleanupProps() {
    System.clearProperty("btrace.feature.manifestLibs");
    System.clearProperty("btrace.test.skipLibs");
    System.clearProperty("btrace.port");
  }

  @Test
  @DisplayName("Dynamic attach with manifest-libs enabled")
  public void dynamicAttach_manifestLibs() throws Exception {
    // Use a timer script with a short interval to ensure timely output
    testDynamic(
        "resources.Main",
        "btrace/OnTimerArgTest.java",
        new String[] {"timer=200"},
        Completion.untilContains("timer"),
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
          assertTrue(stdout.contains("timer"));
        });
  }

  @Test
  @DisplayName("Launch-time agent with manifest-libs enabled")
  public void launchAgent_manifestLibs() throws Exception {
    // Startup mode uses precompiled class and should work with the feature flag
    testStartup(
        "resources.Main",
        "traces/TraceAllTest.class",
        null,
        // Same script and marker as BTraceFunctionalTests.testTraceAll.
        Completion.untilContains("[invocations="),
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
        });
  }
}
