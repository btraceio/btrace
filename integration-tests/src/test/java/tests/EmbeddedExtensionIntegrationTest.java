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
 * End-to-end coverage for the extensions embedded in the published artifact.
 *
 * <p>A Maven or jbang user has no {@code $BTRACE_HOME/extensions} directory to discover, so the
 * published artifact carries the default extensions inside it. This drives a probe that injects one
 * of them against an engine staged the way such a user would have it: a lone {@code btrace.jar}
 * with no sibling {@code extensions/} directory.
 *
 * <p>The paired lean case is what makes the first meaningful. Both stages are identical apart from
 * which engine is present, so a passing embedded case that is not actually resolving anything from
 * inside the jar would show up as the lean case passing too.
 */
public class EmbeddedExtensionIntegrationTest extends RuntimeTest {

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

  /**
   * Resolves a staged engine home and asserts it is isolated from the real distribution.
   *
   * @param property system property naming the staged {@code libs} directory
   * @return the staged directory
   */
  private static Path stagedLibs(String property) {
    Path libs = Paths.get(System.getProperty(property)).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(libs.resolve("btrace.jar")), "staged engine missing: " + libs);
    // The client derives the extension API classpath from btrace.libs' parent. A sibling
    // extensions/ directory here would let filesystem extensions satisfy the probe and both cases
    // below would pass regardless of what the engine contains.
    assertFalse(
        Files.exists(libs.getParent().resolve("extensions")),
        "staged engine must have no sibling extensions directory: " + libs.getParent());
    return libs;
  }

  @Test
  public void embeddedExtensionLinksWithoutADistribution() throws Exception {
    clientBtraceLibs = stagedLibs("btrace.embedded.libs").toString();
    attachDelayMs = 500;

    testDynamic(
        "resources.Main",
        "btrace/EmbeddedExtensionTest.java",
        Completion.untilContains("embedded-extension-linked"),
        (stdout, stderr, retcode, args) ->
            assertTrue(
                stdout.contains("embedded-extension-linked"),
                "probe should link the embedded extension and print through it:\n" + stdout));
  }

  @Test
  public void leanEngineCannotLinkTheSameProbe() throws Exception {
    clientBtraceLibs = stagedLibs("btrace.embedded.lean.libs").toString();
    attachDelayMs = 500;

    testDynamic(
        "resources.Main",
        "btrace/EmbeddedExtensionTest.java",
        // The compile fails, so the probe's output never appears. The diagnostic goes to stderr,
        // which a stdout-only condition would ignore, leaving the harness to sit until its timeout.
        Completion.untilEitherContains("BTrace compilation failed"),
        (stdout, stderr, retcode, args) ->
            assertFalse(
                stdout.contains("embedded-extension-linked"),
                "an engine without embedded extensions must not satisfy the injection:\n"
                    + stdout));
  }
}
