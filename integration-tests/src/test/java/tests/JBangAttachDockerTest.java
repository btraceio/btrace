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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
public class JBangAttachDockerTest {
  @Test
  public void testAttachWithMaskedJar() throws Exception {
    assumeTrue(Boolean.getBoolean("btrace.docker.available"), "Docker not available");
    boolean dockerReady = false;
    try {
      dockerReady = DockerClientFactory.instance().isDockerAvailable();
    } catch (Exception e) {
      dockerReady = false;
    }
    if (!dockerReady) {
      System.err.println("[integration-tests] Docker not reachable by Testcontainers; skipping.");
    }
    Assumptions.assumeTrue(dockerReady, "Docker not reachable by Testcontainers");

    String libsDirPath = System.getProperty("btrace.libs");
    assertTrue(libsDirPath != null && !libsDirPath.isEmpty(), "btrace.libs must be set");
    File libsDir = new File(libsDirPath);
    assertTrue(libsDir.isDirectory(), "btrace.libs must be a directory");

    File btraceJar = new File(libsDir, "btrace.jar");
    assertTrue(btraceJar.isFile(), "btrace.jar missing in libs directory");

    try (GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("eclipse-temurin:21-jdk"))
            .withFileSystemBind(libsDir.getAbsolutePath(), "/btrace/libs", BindMode.READ_ONLY)
            .withCommand("sleep", "300")) {
      container.start();

      String targetSource =
          "public class Target {\n"
              + "  public static void main(String[] args) throws Exception {\n"
              + "    Thread.sleep(1500);\n"
              + "    for (int i = 0; i < 20; i++) {\n"
              + "      work();\n"
              + "      Thread.sleep(200);\n"
              + "    }\n"
              + "    Thread.sleep(2000);\n"
              + "  }\n"
              + "  static void work() {}\n"
              + "}\n";
      Container.ExecResult result =
          container.execInContainer(
              "bash",
              "-lc",
              "set -euo pipefail\n"
                  + "cat > /tmp/Target.java <<'EOF'\n"
                  + targetSource
                  + "EOF\n"
                  + "cat > /tmp/TestTrace.java <<'EOF'\n"
                  + "import static org.openjdk.btrace.core.BTraceUtils.*;\n"
                  + "import org.openjdk.btrace.core.annotations.*;\n"
                  + "\n"
                  + "@BTrace\n"
                  + "public class TestTrace {\n"
                  + "  @OnMethod(clazz = \"Target\", method = \"work\")\n"
                  + "  public static void onWork() {\n"
                  + "    println(\"work\");\n"
                  + "  }\n"
                  + "}\n"
                  + "EOF\n"
                  + "javac /tmp/Target.java\n"
                  + "java -XX:+EnableDynamicAgentLoading -cp /tmp Target > /tmp/target.log 2>&1 &\n"
                  + "pid=$!\n"
                  + "sleep 1\n"
                  + "java -XX:+IgnoreUnrecognizedVMOptions "
                  + "--add-opens java.base/jdk.internal.reflect=ALL-UNNAMED "
                  + "--add-exports java.base/jdk.internal.reflect=ALL-UNNAMED "
                  + "--add-modules jdk.attach "
                  + "--add-exports jdk.attach/sun.tools.attach=ALL-UNNAMED "
                  + "-jar /btrace/libs/btrace.jar ${pid} /tmp/TestTrace.java -v\n"
                  + "kill ${pid} >/dev/null 2>&1 || true\n");

      String output = result.getStdout() + result.getStderr();
      assertEquals(0, result.getExitCode(), "btrace attach command failed\nOutput:\n" + output);
      assertTrue(output.contains("work"), "expected probe output missing");
    }
  }
}
