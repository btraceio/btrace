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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.harness.Completion;

/**
 * Proves the published-plugin external fat-agent path, with every BTrace artifact resolved only
 * from a temporary local repository.
 */
class Issue884PublishedFatAgentE2ETest {
  private static final String VERSION = "3.0.0";

  /**
   * Budget for the nested Gradle builds this test forks.
   *
   * <p>Each fork resolves plugins and dependencies and may compile from a cold cache. 90 seconds
   * was enough on a warm, idle machine but expired routinely on a loaded one, surfacing as
   * "publication did not finish" rather than as anything diagnostic. This bounds a hung build
   * without racing a merely slow one.
   */
  private static final long NESTED_BUILD_TIMEOUT_SECONDS = 300;

  @TempDir Path temporaryDirectory;

  @BeforeAll
  static void setUpHarness() {
    RuntimeTest.classSetup();
  }

  @Test
  void publishedMarkersBuildFatAgentThatServesRealDynamicAttach() throws Exception {
    Path root = Paths.get(System.getProperty("project.dir")).getParent();
    Path repository = temporaryDirectory.resolve("repository");
    publishFixtureRepository(root, repository);
    compileExternalExtensionConsumer(root, repository);
    Path fatAgent = buildExternalConsumer(root, repository);
    Path localEngine =
        repository
            .resolve("io/btrace/btrace/")
            .resolve(VERSION)
            .resolve("btrace-" + VERSION + ".jar");
    assertMaskedAgent(fatAgent);
    assertTrue(Files.isRegularFile(localEngine), "missing staged BTrace engine: " + localEngine);

    String originalLibraries = System.getProperty("btrace.libs");
    String originalClientJar = System.getProperty("btrace.client.jar");
    try {
      System.setProperty("btrace.libs", fatAgent.getParent().toString());
      System.setProperty("btrace.client.jar", localEngine.toString());
      Files.copy(
          fatAgent,
          fatAgent.getParent().resolve("btrace.jar"),
          StandardCopyOption.REPLACE_EXISTING);

      RuntimeTest harness = new RuntimeTest() {};
      harness.reset();
      harness.timeout = 30_000L;
      harness.testDynamic(
          "resources.Main",
          "btrace/OnTimerTest.java",
          null,
          Completion.untilContains("timer"),
          (stdout, stderr, retcode, jfrFile) -> {
            assertTrue(stdout.contains("timer"), stdout);
            assertFalse(stdout.contains("FAILED"), stdout);
            assertTrue(stderr.isEmpty(), stderr);
          });
    } finally {
      if (originalLibraries == null) {
        System.clearProperty("btrace.libs");
      } else {
        System.setProperty("btrace.libs", originalLibraries);
      }
      if (originalClientJar == null) {
        System.clearProperty("btrace.client.jar");
      } else {
        System.setProperty("btrace.client.jar", originalClientJar);
      }
    }
  }

  private void publishFixtureRepository(Path root, Path repository) throws Exception {
    Path engineJar =
        root.resolve("btrace-dist/build/resources/main/v3.0.0-SNAPSHOT/libs/btrace.jar");
    assertTrue(Files.isRegularFile(engineJar), "missing masked engine JAR: " + engineJar);

    publishPluginArtifacts(root, repository);
    publishArtifact(repository, "io/btrace", "btrace", VERSION, engineJar);
  }

  private void publishPluginArtifacts(Path root, Path repository) throws Exception {
    Process process =
        new ProcessBuilder(
                root.resolve("gradlew").toString(),
                "-p",
                root.resolve("btrace-gradle-plugin").toString(),
                "-PbtraceVersion=" + VERSION,
                "-PpublicationRepository=" + repository,
                "publish")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    assertTrue(
        process.waitFor(NESTED_BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "plugin publication did not finish");
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(process.exitValue() == 0, output);
  }

  private void publishArtifact(
      Path repository, String groupPath, String artifact, String version, Path source)
      throws IOException {
    Path directory = repository.resolve(groupPath).resolve(artifact).resolve(version);
    Files.createDirectories(directory);
    Files.copy(source, directory.resolve(artifact + "-" + version + ".jar"));
    Files.writeString(
        directory.resolve(artifact + "-" + version + ".pom"),
        pom("io.btrace", artifact, version, ""),
        StandardCharsets.UTF_8);
  }

  private String pom(String group, String artifact, String version, String extra) {
    return "<project><modelVersion>4.0.0</modelVersion><groupId>"
        + group
        + "</groupId><artifactId>"
        + artifact
        + "</artifactId><version>"
        + version
        + "</version>"
        + extra
        + "</project>";
  }

  private Path buildExternalConsumer(Path root, Path repository) throws Exception {
    Path consumer = temporaryDirectory.resolve("consumer");
    Files.createDirectories(consumer);
    String repositoryUri = repository.toUri().toString();
    Files.writeString(
        consumer.resolve("settings.gradle"),
        // BTrace's plugins and engine resolve only from the staged repository; the plugins'
        // third-party dependencies (Shadow and its tree) come from the Plugin Portal.
        "pluginManagement { repositories {\n"
            + "  exclusiveContent {\n"
            + "    forRepository { maven { url = uri('"
            + repositoryUri
            + "') } }\n"
            + "    filter { includeGroupByRegex('io\\\\.btrace(\\\\..*)?') }\n"
            + "  }\n"
            + "  gradlePluginPortal()\n"
            + "} }\nrootProject.name = 'issue-884-consumer'\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        consumer.resolve("build.gradle"),
        "plugins {\n"
            + "  id 'io.btrace.extension' version '"
            + VERSION
            + "' apply false\n"
            + "  id 'io.btrace.fat-agent' version '"
            + VERSION
            + "'\n"
            + "}\n"
            + "repositories { maven { url = uri('"
            + repositoryUri
            + "') } }\n"
            + "btraceFatAgent { }\n",
        StandardCharsets.UTF_8);
    Process process =
        new ProcessBuilder(
                root.resolve("gradlew").toString(), "-p", consumer.toString(), "fatAgentJar")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    assertTrue(
        process.waitFor(NESTED_BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "external consumer did not finish");
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(process.exitValue() == 0, output);
    assertTrue(output.contains("Staged masked BTrace engine"), output);
    return consumer.resolve("build/libs/btrace-agent-fat.jar");
  }

  private void compileExternalExtensionConsumer(Path root, Path repository) throws Exception {
    Path consumer = temporaryDirectory.resolve("extension-consumer");
    Path source = consumer.resolve("src/main/java/example/ExternalService.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package example;\n"
            + "import io.btrace.core.extensions.ServiceDescriptor;\n"
            + "@ServiceDescriptor public interface ExternalService {}\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        consumer.resolve("settings.gradle"),
        "rootProject.name = 'issue-884-extension-consumer'\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        consumer.resolve("build.gradle"),
        "plugins { id 'java-library' }\n"
            + "repositories { maven { url = uri('"
            + repository.toUri()
            + "') } }\n"
            + "dependencies { compileOnly 'io.btrace:btrace:"
            + VERSION
            + "' }\n",
        StandardCharsets.UTF_8);
    Process process =
        new ProcessBuilder(
                root.resolve("gradlew").toString(), "-p", consumer.toString(), "compileJava")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    assertTrue(process.waitFor(60, TimeUnit.SECONDS), "external extension compile did not finish");
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(process.exitValue() == 0, output);
  }

  private void assertMaskedAgent(Path fatAgent) throws Exception {
    assertTrue(Files.isRegularFile(fatAgent), "missing fat agent: " + fatAgent);
    try (JarFile jar = new JarFile(fatAgent.toFile())) {
      assertTrue(jar.getEntry("io/btrace/boot/Loader.class") != null);
      assertTrue(jar.getEntry("META-INF/btrace/agent/io/btrace/agent/Main.classdata") != null);
      Attributes attributes = jar.getManifest().getMainAttributes();
      assertTrue("io.btrace.boot.Loader".equals(attributes.getValue("Main-Class")));
      assertTrue("io.btrace.boot.Loader".equals(attributes.getValue("Premain-Class")));
      assertTrue("io.btrace.boot.Loader".equals(attributes.getValue("Agent-Class")));
    }
  }
}
