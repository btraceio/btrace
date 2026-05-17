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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
