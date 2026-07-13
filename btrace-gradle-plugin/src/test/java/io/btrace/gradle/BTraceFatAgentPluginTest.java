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


package io.btrace.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the BTrace Fat Agent Gradle plugin using Gradle TestKit.
 */
class BTraceFatAgentPluginTest {

    @TempDir
    Path projectDir;

    private File buildFile;
    private File settingsFile;

    @BeforeEach
    void setUp() throws IOException {
        buildFile = projectDir.resolve("build.gradle").toFile();
        settingsFile = projectDir.resolve("settings.gradle").toFile();

        // Create minimal settings.gradle
        writeFile(settingsFile, "rootProject.name = 'test-project'\n");
    }

    @Test
    @DisplayName("Plugin can be applied successfully")
    void pluginCanBeApplied() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("tasks", "--all")
            .build();

        assertTrue(result.getOutput().contains("fatAgentJar"));
        assertTrue(result.getOutput().contains("stageExtensions"));
        assertTrue(result.getOutput().contains("stageProbes"));
    }

    @Test
    @DisplayName("DSL extension is available")
    void dslExtensionAvailable() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    baseName = 'my-custom-agent'\n" +
            "}\n" +
            "\n" +
            "task printConfig {\n" +
            "    doLast {\n" +
            "        println \"BASE_NAME=${btraceFatAgent.baseName}\"\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("printConfig")
            .build();

        assertTrue(result.getOutput().contains("BASE_NAME=my-custom-agent"));
    }

    @Test
    @DisplayName("stageExtensions task runs successfully with no extensions")
    void stageExtensionsEmptyRuns() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageExtensions")
            .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageExtensions").getOutcome());
    }

    @Test
    @DisplayName("stageProbes task runs successfully with no probes")
    void stageProbesEmptyRuns() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageProbes")
            .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageProbes").getOutcome());
    }

    @Test
    @DisplayName("Output directory can be configured")
    void outputDirectoryConfigurable() throws IOException {
        Path customOutput = projectDir.resolve("custom-output");

        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    outputDir = file('" + customOutput.toString().replace("\\", "/") + "')\n" +
            "}\n" +
            "\n" +
            "task printOutputDir {\n" +
            "    doLast {\n" +
            "        println \"OUTPUT_DIR=${btraceFatAgent.outputDir}\"\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("printOutputDir")
            .build();

        assertTrue(result.getOutput().contains("OUTPUT_DIR=" + customOutput.toString()));
    }

    @Test
    @DisplayName("Manifest attributes can be added")
    void manifestAttributesConfigurable() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    manifestAttributes['Custom-Attribute'] = 'custom-value'\n" +
            "}\n" +
            "\n" +
            "task printManifest {\n" +
            "    doLast {\n" +
            "        println \"ATTRS=${btraceFatAgent.manifestAttributes}\"\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("printManifest")
            .build();

        // Gradle map format uses colon separator
        assertTrue(result.getOutput().contains("Custom-Attribute:custom-value"));
    }

    @Test
    @DisplayName("bundledProbes DSL is available")
    void bundledProbesDslAvailable() throws IOException {
        // Create a dummy probes directory
        Path probesDir = projectDir.resolve("probes");
        Files.createDirectories(probesDir);

        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    bundledProbes {\n" +
            "        from 'probes'\n" +
            "        include 'MyProbe'\n" +
            "        exclude 'TestProbe'\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "task printProbeBundle {\n" +
            "    doLast {\n" +
            "        println \"HAS_PROBES=${btraceFatAgent.probeBundle.hasProbes()}\"\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("printProbeBundle")
            .build();

        // probeBundle should report having probes since we configured a directory
        assertTrue(result.getOutput().contains("HAS_PROBES=true"));
    }

    @Test
    @DisplayName("Fat agent packages nested bundled probe binary names")
    void packagesNestedBundledProbe() throws IOException {
        Path probe = projectDir.resolve("probes/com/example/NestedProbe.class");
        Files.createDirectories(probe.getParent());
        Files.write(probe, new byte[] {0, 1, 2, 3});
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  bundledProbes {\n"
                        + "    from 'probes'\n"
                        + "    include 'com.example.NestedProbe'\n"
                        + "  }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("fatAgentJar").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":fatAgentJar").getOutcome());
        try (JarFile jar =
                new JarFile(projectDir.resolve("build/libs/btrace-agent-fat.jar").toFile())) {
            assertNotNull(jar.getEntry("META-INF/btrace-probes/com/example/NestedProbe.class"));
        }
    }

    @Test
    @DisplayName("Launched fat agent executes and tears down a nested bundled probe")
    void launchedFatAgentExecutesAndTearsDownBundledProbe() throws Exception {
        Path agentSource = projectDir.resolve("src/main/java/io/btrace/agent/Main.java");
        Path appSource = projectDir.resolve("src/main/java/demo/App.java");
        Path probeSource = projectDir.resolve("probe-src/com/example/NestedProbe.java");
        Files.createDirectories(agentSource.getParent());
        Files.createDirectories(appSource.getParent());
        Files.createDirectories(probeSource.getParent());
        Files.writeString(
                agentSource,
                "package io.btrace.agent;\n"
                        + "import java.io.InputStream;\n"
                        + "public final class Main {\n"
                        + "  public static void premain(String name) throws Exception {\n"
                        + "    String path = \"META-INF/btrace-probes/\" + name.replace('.', '/') + \".class\";\n"
                        + "    ClassLoader loader = Main.class.getClassLoader();\n"
                        + "    try (InputStream in = loader != null ? loader.getResourceAsStream(path) : ClassLoader.getSystemResourceAsStream(path)) {\n"
                        + "      if (in == null) throw new IllegalStateException(\"missing \" + path);\n"
                        + "      Class<?> probe = new ProbeLoader().define(in.readAllBytes());\n"
                        + "      probe.getMethod(\"start\").invoke(null);\n"
                        + "      Runtime.getRuntime().addShutdownHook(new Thread(() -> {\n"
                        + "        try { probe.getMethod(\"stop\").invoke(null); }\n"
                        + "        catch (Exception e) { throw new RuntimeException(e); }\n"
                        + "      }));\n"
                        + "    }\n"
                        + "  }\n"
                        + "  private static final class ProbeLoader extends ClassLoader {\n"
                        + "    Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }\n"
                        + "  }\n"
                        + "}\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                appSource,
                "package demo;\n"
                        + "public final class App {\n"
                        + "  public static void main(String[] args) { System.out.println(\"APP_RAN\"); }\n"
                        + "}\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                probeSource,
                "package com.example;\n"
                        + "public final class NestedProbe {\n"
                        + "  public static void start() { System.out.println(\"BUNDLED_PROBE_EXECUTED\"); }\n"
                        + "  public static void stop() { System.out.println(\"BUNDLED_PROBE_TORN_DOWN\"); }\n"
                        + "}\n",
                StandardCharsets.UTF_8);
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "tasks.register('compileBundledProbe', JavaCompile) {\n"
                        + "  source = fileTree('probe-src') { include '**/*.java' }\n"
                        + "  classpath = files()\n"
                        + "  destinationDirectory = layout.buildDirectory.dir('compiled-probes')\n"
                        + "  options.release = 11\n"
                        + "}\n"
                        + "tasks.named('stageProbes') { dependsOn 'compileBundledProbe' }\n"
                        + "btraceFatAgent {\n"
                        + "  agentJarTask = 'jar'\n"
                        + "  manifestAttributes['Premain-Class'] = 'io.btrace.agent.Main'\n"
                        + "  manifestAttributes['Agent-Class'] = 'io.btrace.agent.Main'\n"
                        + "  bundledProbes {\n"
                        + "    from layout.buildDirectory.dir('compiled-probes').get().asFile\n"
                        + "    include 'com.example.NestedProbe'\n"
                        + "  }\n"
                        + "}\n");

        createRunner().withArguments("fatAgentJar", "classes").build();

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process =
                new ProcessBuilder(
                                java,
                                "-javaagent:"
                                        + projectDir.resolve(
                                                "build/libs/btrace-agent-fat.jar")
                                        + "=com.example.NestedProbe",
                                "-cp",
                                projectDir.resolve("build/classes/java/main").toString(),
                                "demo.App")
                        .redirectErrorStream(true)
                        .start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "launched JVM did not terminate");
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("BUNDLED_PROBE_EXECUTED"), output);
        assertTrue(output.contains("APP_RAN"), output);
        assertTrue(output.contains("BUNDLED_PROBE_TORN_DOWN"), output);
    }

    @Test
    @DisplayName("Named bundled probes must exist")
    void missingNamedBundledProbeFailsBuild() throws IOException {
        Files.createDirectories(projectDir.resolve("probes"));
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  bundledProbes {\n"
                        + "    from 'probes'\n"
                        + "    include 'com.example.MissingProbe'\n"
                        + "  }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("stageProbes").buildAndFail();

        assertTrue(result.getOutput().contains("Named bundled probe 'com.example.MissingProbe'"));
        assertTrue(result.getOutput().contains("was not found"));
    }

    @Test
    @DisplayName("Bundled probe names cannot escape the resource namespace")
    void invalidBundledProbeNameFailsBuild() throws IOException {
        Files.createDirectories(projectDir.resolve("probes"));
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  bundledProbes {\n"
                        + "    from 'probes'\n"
                        + "    include '../outside'\n"
                        + "  }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("stageProbes").buildAndFail();

        assertTrue(result.getOutput().contains("Invalid bundled probe binary name: '../outside'"));
    }

    @Test
    @DisplayName("Auto-discover can be disabled")
    void autoDiscoverConfigurable() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    autoDiscover = false\n" +
            "}\n" +
            "\n" +
            "task printAutoDiscover {\n" +
            "    doLast {\n" +
            "        println \"AUTO_DISCOVER=${btraceFatAgent.autoDiscover}\"\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("printAutoDiscover")
            .build();

        assertTrue(result.getOutput().contains("AUTO_DISCOVER=false"));
    }

    @Test
    @DisplayName("Build fails gracefully with invalid extension")
    void failsGracefullyWithInvalidExtension() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    embedExtensions {\n" +
            "        project(':non-existent-project')\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageExtensions")
            .buildAndFail();

        // Build should fail with message about non-existent project
        assertTrue(result.getOutput().contains("non-existent-project") ||
                   result.getOutput().contains("Project with path") ||
                   result.getOutput().contains("FAILED"));
    }

    @Test
    @DisplayName("Staging directory is created")
    void stagingDirectoryCreated() throws IOException {
        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageExtensions")
            .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageExtensions").getOutcome());

        // Staging directory should exist
        Path stagingDir = projectDir.resolve("build/fat-agent-staging");
        assertTrue(Files.exists(stagingDir), "Staging directory should be created");
    }

    @Test
    @DisplayName("Registry source fails clearly for unknown extension id")
    void registrySourceFailsForUnknownId() throws IOException {
        Path registry = projectDir.resolve("extensions.json");
        Files.writeString(
            registry,
            "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"extensions\": []\n" +
            "}\n",
            StandardCharsets.UTF_8);

        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    registryUrl = '" + registry.toUri().toString() + "'\n" +
            "    embedExtensions {\n" +
            "        registry('missing-ext')\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageExtensions")
            .buildAndFail();

        assertTrue(result.getOutput().contains("Unknown extension id: missing-ext"));
    }

    private GradleRunner createRunner() {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .forwardOutput();
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
