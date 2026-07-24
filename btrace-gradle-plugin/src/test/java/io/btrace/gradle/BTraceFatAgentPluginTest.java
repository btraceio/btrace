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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
    @DisplayName("external builds assemble a fat agent from one pinned masked engine")
    void externalBuildUsesPinnedMaskedEngine() throws IOException {
        writeMaskedEngineFixture("3.0.0");
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "repositories { maven { url = uri('repo') } }\n"
                        + "version = '99.0.0-extension'\n"
                        + "btraceFatAgent { btraceVersion = '3.0.0' }\n"
                        + "tasks.register('printEngine') { doLast {\n"
                        + "  configurations.btraceEngine.dependencies.each { println \"ENGINE=${it.group}:${it.name}:${it.version}\" }\n"
                        + "} }\n");

        BuildResult result = createRunner().withArguments("fatAgentJar", "printEngine").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageBTraceEngine").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":fatAgentJar").getOutcome());
        assertTrue(result.getOutput().contains("ENGINE=io.btrace:btrace:3.0.0"));
        try (JarFile jar =
                new JarFile(projectDir.resolve("build/libs/btrace-agent-fat.jar").toFile())) {
            assertNotNull(jar.getEntry("io/btrace/boot/Loader.class"));
            assertNotNull(
                    jar.getEntry("META-INF/btrace/agent/io/btrace/agent/Main.classdata"));
            assertEquals("io.btrace.boot.Loader", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertEquals("io.btrace.boot.Loader", jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertEquals("io.btrace.boot.Loader", jar.getManifest().getMainAttributes().getValue("Agent-Class"));
            assertEquals("btrace-agent-fat.jar", jar.getManifest().getMainAttributes().getValue("Boot-Class-Path"));
        }
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
        writeMinimalMaskedAgentFixture();
        Path probe = projectDir.resolve("probes/com/example/NestedProbe.class");
        Files.createDirectories(probe.getParent());
        Files.write(probe, new byte[] {0, 1, 2, 3});
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  agentJarTask = 'jar'\n"
                        + "  bundledProbes {\n"
                        + "    from 'probes'\n"
                        + "    include 'com.example.NestedProbe'\n"
                        + "  }\n"
                        + "}\n"
                        + "jar { manifest { attributes('Main-Class': 'io.btrace.boot.Loader', 'Premain-Class': 'io.btrace.boot.Loader', 'Agent-Class': 'io.btrace.boot.Loader', 'BTrace-Agent-Main': 'io.btrace.agent.Main', 'Boot-Class-Path': 'test-project.jar') } }\n");

        BuildResult result = createRunner().withArguments("fatAgentJar").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":fatAgentJar").getOutcome());
        try (JarFile jar =
                new JarFile(projectDir.resolve("build/libs/btrace-agent-fat.jar").toFile())) {
            assertNotNull(jar.getEntry("META-INF/btrace-probes/com/example/NestedProbe.class"));
        }
    }

    @Test
    @DisplayName("stageProbes reruns when a bundled probe class changes")
    void stageProbesTracksProbeDirectoryContents() throws IOException {
        Path probe = projectDir.resolve("probes/com/example/NestedProbe.class");
        Files.createDirectories(probe.getParent());
        Files.write(probe, new byte[] {0, 1, 2, 3});
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  bundledProbes { from 'probes' }\n"
                        + "}\n");

        BuildResult first = createRunner().withArguments("stageProbes").build();
        BuildResult unchanged = createRunner().withArguments("stageProbes").build();
        Files.write(probe, new byte[] {4, 5, 6, 7});
        BuildResult changed = createRunner().withArguments("stageProbes").build();

        assertEquals(TaskOutcome.SUCCESS, first.task(":stageProbes").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":stageProbes").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":stageProbes").getOutcome());
        assertEquals(
                (byte) 4,
                Files.readAllBytes(
                                projectDir.resolve(
                                        "build/fat-agent-staging/META-INF/btrace-probes/com/example/NestedProbe.class"))
                        [0]);
    }

    @Test
    @DisplayName("stageProbes reruns when bundled probe selection changes")
    void stageProbesTracksProbeSelection() throws IOException {
        Path firstProbe = projectDir.resolve("probes/com/example/FirstProbe.class");
        Path secondProbe = projectDir.resolve("probes/com/example/SecondProbe.class");
        Files.createDirectories(firstProbe.getParent());
        Files.write(firstProbe, new byte[] {0});
        Files.write(secondProbe, new byte[] {1});
        writeProbeSelection("com.example.FirstProbe");

        BuildResult first = createRunner().withArguments("stageProbes").build();
        writeProbeSelection("com.example.SecondProbe");
        BuildResult changed = createRunner().withArguments("stageProbes").build();

        assertEquals(TaskOutcome.SUCCESS, first.task(":stageProbes").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":stageProbes").getOutcome());
        Path stagedRoot = projectDir.resolve("build/fat-agent-staging/META-INF/btrace-probes");
        assertTrue(Files.notExists(stagedRoot.resolve("com/example/FirstProbe.class")));
        assertTrue(Files.exists(stagedRoot.resolve("com/example/SecondProbe.class")));
    }

    @Test
    @DisplayName("Launched fat agent executes and tears down a nested bundled probe")
    void launchedFatAgentExecutesAndTearsDownBundledProbe() throws Exception {
        Path agentSource = projectDir.resolve("src/main/java/io/btrace/boot/Loader.java");
        Path appSource = projectDir.resolve("src/main/java/demo/App.java");
        Path probeSource = projectDir.resolve("probe-src/com/example/NestedProbe.java");
        Path maskedAgentMain =
                projectDir.resolve(
                        "src/main/resources/META-INF/btrace/agent/io/btrace/agent/Main.classdata");
        Files.createDirectories(agentSource.getParent());
        Files.createDirectories(appSource.getParent());
        Files.createDirectories(probeSource.getParent());
        Files.createDirectories(maskedAgentMain.getParent());
        Files.write(maskedAgentMain, new byte[] {0});
        Files.writeString(
                agentSource,
                "package io.btrace.boot;\n"
                        + "import java.io.InputStream;\n"
                        + "public final class Loader {\n"
                        + "  public static void premain(String name) throws Exception {\n"
                        + "    String path = \"META-INF/btrace-probes/\" + name.replace('.', '/') + \".class\";\n"
                        + "    ClassLoader loader = Loader.class.getClassLoader();\n"
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
                        + "  bundledProbes {\n"
                        + "    from layout.buildDirectory.dir('compiled-probes').get().asFile\n"
                        + "    include 'com.example.NestedProbe'\n"
                        + "  }\n"
                        + "}\n"
                        + "jar { manifest { attributes('Main-Class': 'io.btrace.boot.Loader', 'Premain-Class': 'io.btrace.boot.Loader', 'Agent-Class': 'io.btrace.boot.Loader', 'BTrace-Agent-Main': 'io.btrace.agent.Main', 'Boot-Class-Path': 'test-project.jar') } }\n");

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
    @DisplayName("Conventional agent JARs fail the masked fat-agent contract")
    void conventionalAgentJarFailsClearly() throws IOException {
        Path agentSource = projectDir.resolve("src/main/java/io/btrace/agent/Main.java");
        Files.createDirectories(agentSource.getParent());
        Files.writeString(
                agentSource,
                "package io.btrace.agent; public final class Main {}\n",
                StandardCharsets.UTF_8);
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent { agentJarTask = 'jar' }\n");

        BuildResult result = createRunner().withArguments("fatAgentJar").buildAndFail();

        assertTrue(result.getOutput().contains("Invalid masked BTrace fat JAR"));
        assertTrue(result.getOutput().contains("missing io/btrace/boot/Loader.class"));
        assertTrue(result.getOutput().contains("reference the btraceJar task"));
    }

    @Test
    @DisplayName("Fat agents require the masked agent section")
    void fatAgentWithoutMaskedAgentMainFailsClearly() throws IOException {
        Path loaderSource = projectDir.resolve("src/main/java/io/btrace/boot/Loader.java");
        Files.createDirectories(loaderSource.getParent());
        Files.writeString(
                loaderSource,
                "package io.btrace.boot; public final class Loader {}\n",
                StandardCharsets.UTF_8);
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent { agentJarTask = 'jar' }\n");

        BuildResult result = createRunner().withArguments("fatAgentJar").buildAndFail();

        assertTrue(result.getOutput().contains("Invalid masked BTrace fat JAR"));
        assertTrue(
                result.getOutput()
                        .contains(
                                "missing META-INF/btrace/agent/io/btrace/agent/Main.classdata"));
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
    @DisplayName("Embedded packaging transfers privileged permission metadata")
    void embeddedPackagingTransfersPermissions() throws IOException {
        Path extensionDir = writeExtensionFixture("NETWORK");
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "    embedExtensions { file('"
                        + extensionDir.toString().replace("\\", "/")
                        + "') }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("stageExtensions").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageExtensions").getOutcome());
        Properties staged = new Properties();
        try (InputStream input =
                Files.newInputStream(
                        projectDir.resolve(
                                "build/fat-agent-staging/META-INF/btrace-extensions/privileged-test/extension.properties"))) {
            staged.load(input);
        }
        assertEquals("NETWORK", staged.getProperty("permissions"));
        assertEquals("test.ext.Service", staged.getProperty("services"));
    }

    @Test
    @DisplayName("Embedded packaging preserves an explicitly empty permission set")
    void embeddedPackagingPreservesEmptyPermissions() throws IOException {
        Path extensionDir = writeExtensionFixture("");
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "    embedExtensions { file('"
                        + extensionDir.toString().replace("\\", "/")
                        + "') }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("stageExtensions").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageExtensions").getOutcome());
        Properties staged = new Properties();
        try (InputStream input =
                Files.newInputStream(
                        projectDir.resolve(
                                "build/fat-agent-staging/META-INF/btrace-extensions/privileged-test/extension.properties"))) {
            staged.load(input);
        }
        assertEquals("", staged.getProperty("permissions"));
    }

    @Test
    @DisplayName("Embedded packaging fails closed when permission metadata is missing")
    void embeddedPackagingRejectsMissingPermissions() throws IOException {
        Path extensionDir = writeExtensionFixture(null);
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "    embedExtensions { file('"
                        + extensionDir.toString().replace("\\", "/")
                        + "') }\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments("stageExtensions").buildAndFail();

        assertTrue(result.getOutput().contains("missing BTrace-Extension-Permissions"));
        assertTrue(
                result.getOutput().contains("refusing to default permissions to an empty set"));
    }

    @Test
    @DisplayName("Extension API manifest metadata is retained when staging")
    void extensionApiManifestMetadataRetained() throws IOException {
        Path extensionDir = projectDir.resolve("sample-extension");
        Files.createDirectories(extensionDir);
        createExtensionApiJar(extensionDir.resolve("sample-api.jar"));
        createExtensionImplJar(extensionDir.resolve("sample-impl.jar"));

        writeFile(buildFile,
            "plugins {\n" +
            "    id 'io.btrace.fat-agent'\n" +
            "}\n" +
            "\n" +
            "btraceFatAgent {\n" +
            "    autoDiscover = false\n" +
            "    embedExtensions {\n" +
            "        file('sample-extension')\n" +
            "    }\n" +
            "}\n"
        );

        BuildResult result = createRunner()
            .withArguments("stageExtensions")
            .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageExtensions").getOutcome());
        String properties = Files.readString(
            projectDir.resolve(
                "build/fat-agent-staging/META-INF/btrace-extensions/sample/extension.properties"));
        assertTrue(properties.contains("services=com.example.SampleService"));
        assertTrue(properties.contains("permissions=THREADS"));
        assertTrue(properties.contains("btrace.api.version=3.0+"));
        assertEquals(
            "com.example.SampleServiceImpl",
            Files.readString(
                projectDir.resolve(
                    "build/fat-agent-staging/META-INF/services/com.example.SampleService")));
    }

    private GradleRunner createRunner() {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .forwardOutput();
    }

    private void writeProbeSelection(String probeName) throws IOException {
        writeFile(
                buildFile,
                "plugins { id 'io.btrace.fat-agent' }\n"
                        + "btraceFatAgent {\n"
                        + "  bundledProbes {\n"
                        + "    from 'probes'\n"
                        + "    include '"
                        + probeName
                        + "'\n"
                        + "  }\n"
                        + "}\n");
    }

    private void writeMinimalMaskedAgentFixture() throws IOException {
        Path loaderSource = projectDir.resolve("src/main/java/io/btrace/boot/Loader.java");
        Path maskedAgentMain =
                projectDir.resolve(
                        "src/main/resources/META-INF/btrace/agent/io/btrace/agent/Main.classdata");
        Files.createDirectories(loaderSource.getParent());
        Files.createDirectories(maskedAgentMain.getParent());
        Files.writeString(
                loaderSource,
                "package io.btrace.boot; public final class Loader {}\n",
                StandardCharsets.UTF_8);
        Files.write(maskedAgentMain, new byte[] {0});
    }

    private void writeMaskedEngineFixture(String version) throws IOException {
        Path engine =
                projectDir.resolve("repo/io/btrace/btrace/").resolve(version).resolve("btrace-" + version + ".jar");
        Files.createDirectories(engine.getParent());
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Main-Class", "io.btrace.boot.Loader");
        attributes.putValue("Premain-Class", "io.btrace.boot.Loader");
        attributes.putValue("Agent-Class", "io.btrace.boot.Loader");
        attributes.putValue("Boot-Class-Path", "btrace.jar");
        attributes.putValue("BTrace-Agent-Main", "io.btrace.agent.Main");
        attributes.putValue("BTrace-Client-Main", "io.btrace.client.Main");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(engine), manifest)) {
            output.putNextEntry(new JarEntry("io/btrace/boot/Loader.class"));
            output.write(new byte[] {0});
            output.closeEntry();
            output.putNextEntry(
                    new JarEntry("META-INF/btrace/agent/io/btrace/agent/Main.classdata"));
            output.write(new byte[] {0});
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/btrace/client/fixture.classdata"));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        Files.writeString(
                engine.resolveSibling("btrace-" + version + ".pom"),
                "<project><modelVersion>4.0.0</modelVersion><groupId>io.btrace</groupId>"
                        + "<artifactId>btrace</artifactId><version>"
                        + version
                        + "</version></project>",
                StandardCharsets.UTF_8);
    }

    private Path writeExtensionFixture(String permissions) throws IOException {
        Path extensionDir = projectDir.resolve("privileged-extension");
        Files.createDirectories(extensionDir);
        Files.writeString(
                extensionDir.resolve("extension.properties"),
                "id=privileged-test\nversion=1.0.0\n",
                StandardCharsets.UTF_8);

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("BTrace-Extension-Id", "privileged-test");
        attributes.putValue("BTrace-Extension-Version", "1.0.0");
        attributes.putValue("BTrace-Extension-Name", "Privileged test");
        attributes.putValue("BTrace-Extension-Services", "test.ext.Service");
        if (permissions != null) {
            attributes.putValue("BTrace-Extension-Permissions", permissions);
        }
        try (OutputStream output =
                        Files.newOutputStream(
                                extensionDir.resolve("privileged-test-api.jar"));
                JarOutputStream ignored = new JarOutputStream(output, manifest)) {
            // The manifest is the only content needed by this packaging fixture.
        }
        return extensionDir;
    }

    private void createExtensionApiJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("BTrace-Extension-Id", "sample");
        attributes.putValue("BTrace-Extension-Version", "1.2.3");
        attributes.putValue("BTrace-Extension-Name", "Sample extension");
        attributes.putValue("BTrace-API-Version", "3.0+");
        attributes.putValue("BTrace-Java-Version", "8+");
        attributes.putValue("BTrace-Extension-Services", "com.example.SampleService");
        attributes.putValue("BTrace-Extension-Permissions", "THREADS");
        try (JarOutputStream ignored =
            new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            // The staging regression only needs canonical manifest metadata.
        }
    }

    private void createExtensionImplJar(Path jarPath) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("META-INF/services/com.example.SampleService"));
            jar.write("com.example.SampleServiceImpl".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
