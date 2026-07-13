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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tests.harness.Completion;

/**
 * Integration tests for Extension lifecycle management.
 *
 * <p>Validates that Extension.initialize() and close() are called correctly during script execution
 * and detachment.
 */
public class ExtensionLifecycleIntegrationTest extends RuntimeTest {

  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    // Use an ephemeral port to avoid conflicts with leaked agents from other test classes
    try (ServerSocket ss = new ServerSocket(0)) {
      btracePort = ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find a free port", e);
    }
  }

  @Test
  public void testExtensionMethodCalled() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleFullTest.java",
        new String[] {"extensionCloseTest=true"},
        Completion.untilContains("LIFECYCLE: extension method called"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");

            // Validate extension method was called
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called. stdout: " + stdout);
          }
        });
  }

  @Test
  public void testExtensionCloseCalledOnError() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleErrorTest.java",
        new String[] {"extensionCloseTest=true"},
        Completion.untilContains("LIFECYCLE: extension method called", "Triggering error exit"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertEquals(1, retcode, "Script should have exited with code 1");
            // Extension should still be called even on error exit
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called. stdout: " + stdout);
            assertTrue(
                stdout.contains("Triggering error exit"),
                "Error exit message not found. stdout: " + stdout);
          }
        });
  }

  @Test
  public void testMultipleExtensionsAllClosed() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleMultipleTest.java",
        new String[] {"extensionCloseTest=true"},
        Completion.untilContains(
            "LIFECYCLE: printer extension called", "LIFECYCLE: metrics extension called"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");

            // Validate both extensions were called
            assertTrue(
                stdout.contains("LIFECYCLE: printer extension called"),
                "Printer extension method not called. stdout: " + stdout);
            assertTrue(
                stdout.contains("LIFECYCLE: metrics extension called"),
                "Metrics extension method not called. stdout: " + stdout);
          }
        });
  }
}
