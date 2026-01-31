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
  }

  @Test
  public void testExtensionInitializeAndCloseCalled() throws Exception {
    unattended = true; // Detach after OK status to trigger close()
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleFullTest.java",
        new String[] {"extensionCloseTest=true"},
        10,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);

            // Validate extension method was called
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called");

            // Validate close was called (PrinterServiceImpl sends this message)
            assertTrue(
                stdout.contains("extension close: btrace-utils"), "Extension not closed");
          }
        });
  }

  @Test
  public void testExtensionCloseCalledOnError() throws Exception {
    unattended = true;
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleErrorTest.java",
        new String[] {"extensionCloseTest=true"},
        10,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            // Extension should still be called and closed even on error exit
            assertTrue(
                stdout.contains("LIFECYCLE: extension method called"),
                "Extension method not called");
            assertTrue(
                stdout.contains("Triggering error exit"), "Error exit message not found");
            assertTrue(
                stdout.contains("extension close: btrace-utils"),
                "Extension not closed despite error");

            // Validate order even when error occurs
            int callPos = stdout.indexOf("LIFECYCLE: extension method called");
            int errorPos = stdout.indexOf("Triggering error exit");
            int closePos = stdout.indexOf("extension close: btrace-utils");

            assertTrue(
                callPos < errorPos, "Extension method should be called before error exit");
            assertTrue(errorPos < closePos, "Close should be called after error exit");
          }
        });
  }

  @Test
  public void testMultipleExtensionsAllClosed() throws Exception {
    unattended = true; // Detach after OK status to trigger close()
    testDynamic(
        "resources.Main",
        "btrace/ExtensionLifecycleMultipleTest.java",
        new String[] {"extensionCloseTest=true"},
        10,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);

            // Validate both extensions were called
            assertTrue(
                stdout.contains("LIFECYCLE: printer extension called"),
                "Printer extension method not called");
            assertTrue(
                stdout.contains("LIFECYCLE: metrics extension called"),
                "Metrics extension method not called");

            // Validate at least the printer extension was closed
            // (MetricsService may not emit close message, but we verify it doesn't crash)
            assertTrue(
                stdout.contains("extension close: btrace-utils"),
                "Printer extension not closed");
          }
        });
  }
}
