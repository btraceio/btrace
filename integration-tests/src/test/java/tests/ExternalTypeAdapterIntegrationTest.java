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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tests.harness.Completion;

/**
 * End-to-end integration test for the @ExternalType annotation processor.
 *
 * <p>Verifies that: 1. btracec accepts a probe that uses a service backed by @ExternalType adapters
 * 2. The agent loads the extension without errors 3. The generated ExternalDataType$Ext adapter
 * resolves resources.ExternalData at runtime via the target application's classloader (TCCL) and
 * successfully dispatches both a virtual instance call (value()) and a static call (tag())
 */
public class ExternalTypeAdapterIntegrationTest extends RuntimeTest {

  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    try (ServerSocket ss = new ServerSocket(0)) {
      btracePort = ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find a free port", e);
    }
  }

  @Test
  public void testExternalTypeAdapterTagAndValue() throws Exception {
    Path integrationBuild =
        Paths.get(System.getProperty("project.dir"), "build").toAbsolutePath().normalize();
    Path stagedClientLibs =
        Paths.get(System.getProperty("btrace.externalType.client.libs")).toAbsolutePath().normalize();
    Path stagedExtensions =
        Paths.get(System.getProperty("btrace.externalType.extensions")).toAbsolutePath().normalize();
    assertTrue(stagedClientLibs.startsWith(integrationBuild), "staged client libs must be isolated");
    assertTrue(stagedExtensions.startsWith(integrationBuild), "staged extensions must be isolated");
    assertTrue(Files.isRegularFile(stagedClientLibs.resolve("btrace.jar")));
    assertTrue(Files.isDirectory(stagedExtensions.resolve("btrace-ext-test")));
    Path releaseExtensions =
        Paths.get(System.getProperty("btrace.libs")).getParent().resolve("extensions");
    assertFalse(
        Files.exists(releaseExtensions.resolve("btrace-ext-test")),
        "test extension must not be staged in the release distribution");
    targetExtensionPath = stagedExtensions.toString();
    clientBtraceLibs = stagedClientLibs.toString();
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExternalTypeAdapterTest.java",
        // Wait for the probe's actual output — the same signals the validator asserts on — so the
        // harness never tears the target down before the async retransform of the already-loaded
        // resources.Main class has fired the probe.
        Completion.untilContains("tag=ext-data-ok", "value=42"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(
                stdout.contains("FAILED"), "Probe should not have failed. stderr: " + stderr);

            // Static dispatch via TCCL: ExternalDataType$Ext.tag() -> ExternalData.tag()
            assertTrue(
                stdout.contains("tag=ext-data-ok"),
                "@ExternalType static dispatch failed. stdout: " + stdout);

            // Virtual dispatch: ExternalDataType$Ext.value(data) -> ExternalData.value()
            assertTrue(
                stdout.contains("value=42"),
                "@ExternalType virtual dispatch failed. stdout: " + stdout);
          }
        });
  }
}
