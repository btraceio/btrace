/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The agent jar is appended to the boot class path of the target JVM. A javac running inside that
 * JVM, such as the one behind single-file source launch, must not discover an annotation processor
 * from it: the jar carries no {@code javax.annotation.processing.Processor} service entry.
 */
public class AgentSourceLaunchIntegrationTest extends RuntimeTest {
  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void sourceFileLaunchWithAgentRuns() throws Exception {
    Path agent = locateAgent();
    assertNotNull(agent, "Missing btrace.jar");
    Path source =
        Paths.get(System.getProperty("project.dir"))
            .resolve("src/test/resources/sourcelaunch/HelloSource.java");

    List<String> command =
        Arrays.asList(
            resolveTestJavaHome() + "/bin/java",
            "-Dbtrace.suppressJavaDeprecationWarning=true",
            "-javaagent:" + agent + "=port=0,telemetry=false",
            source.toString());
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().remove("BTRACE_HOME");
    pb.redirectErrorStream(true);
    Process process = pb.start();
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    Thread reader =
        new Thread(
            () -> {
              try (InputStream in = process.getInputStream()) {
                in.transferTo(captured);
              } catch (Exception ignored) {
                // the stream closes when the process is destroyed
              }
            });
    reader.setDaemon(true);
    reader.start();
    try {
      if (!process.waitFor(120, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
      reader.join(5_000);
    } finally {
      process.destroyForcibly();
    }
    String output = captured.toString("UTF-8");

    assertTrue(output.contains("ready"), "source launch did not reach main:\n" + output);
    assertFalse(
        output.contains("Could not find or load main class"),
        "source launch failed to start:\n" + output);
    assertFalse(
        output.contains("NoClassDefFoundError"), "javac failed inside the target:\n" + output);
  }
}
