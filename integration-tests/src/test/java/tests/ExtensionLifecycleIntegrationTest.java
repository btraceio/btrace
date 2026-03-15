/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Extension lifecycle management.
 *
 * <p>Validates that Extension.initialize() and close() are called correctly during script
 * execution and detachment.
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
    waitForPort(2020, 30_000);
  }

  /**
   * Waits until the given port is available (not in use by a previous test's agent).
   */
  private static void waitForPort(int port, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try (ServerSocket ss = new ServerSocket(port)) {
        ss.setReuseAddress(true);
        return; // port is free
      } catch (Exception e) {
        // port still in use
        try {
          Thread.sleep(200);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
    System.err.println("WARNING: port " + port + " still unavailable after " + timeoutMs + "ms");
  }

  @Test
  public void testExtensionInitializeAndCloseCalled() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleFullTest.java",
        new String[] {"extensionCloseTest=true"},
        2,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");

            // Validate extension method was called
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called");
          }
        });
  }

  @Test
  public void testExtensionCloseCalledOnError() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleErrorTest.java",
        new String[] {"extensionCloseTest=true"},
        2,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            // Extension should still be called even on error exit
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called");
            assertTrue(
                stdout.contains("Triggering error exit"), "Error exit message not found");
          }
        });
  }

  @Test
  public void testMultipleExtensionsAllClosed() throws Exception {
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleMultipleTest.java",
        new String[] {"extensionCloseTest=true"},
        3,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");

            // Validate both extensions were called
            assertTrue(
                stdout.contains("LIFECYCLE: printer extension called"),
                "Printer extension method not called");
            assertTrue(
                stdout.contains("LIFECYCLE: metrics extension called"),
                "Metrics extension method not called");
          }
        });
  }
}
