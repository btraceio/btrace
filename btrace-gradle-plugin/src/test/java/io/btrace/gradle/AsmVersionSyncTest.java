/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the one dependency version this build is forced to duplicate.
 *
 * <p>{@code btrace-gradle-plugin} is an included build, so it cannot resolve the root build's
 * version catalog and has to hard-code its ASM coordinates. That duplication has already drifted
 * once: a release-prep commit rebased on a stale checkout silently reverted the plugin's ASM to an
 * older version than the catalog, and nothing failed. This test turns that silent drift into a
 * build failure.
 */
class AsmVersionSyncTest {

    private static final Pattern CATALOG_ASM_VERSION =
            Pattern.compile("version\\s*\\(\\s*'asm'\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern PLUGIN_ASM_COORDINATE =
            Pattern.compile("['\"]org\\.ow2\\.asm:(asm[\\w-]*):([^'\"]+)['\"]");

    @Test
    @DisplayName("plugin ASM version matches the root version catalog")
    void asmVersionMatchesRootCatalog() throws IOException {
        Path rootSettings = requiredPath("btrace.test.rootSettingsFile");
        Assumptions.assumeTrue(
                Files.isRegularFile(rootSettings),
                "root settings.gradle not reachable - plugin is being built standalone");

        String catalogVersion = catalogAsmVersion(read(rootSettings));
        Set<String> pluginVersions = pluginAsmVersions(read(pluginBuildFile()));

        assertTrue(!pluginVersions.isEmpty(), "no org.ow2.asm coordinates found in the plugin build");
        assertEquals(
                1,
                pluginVersions.size(),
                "btrace-gradle-plugin declares conflicting ASM versions: " + pluginVersions);
        assertEquals(
                catalogVersion,
                pluginVersions.iterator().next(),
                "btrace-gradle-plugin's hard-coded ASM version has drifted from version('asm', ...) "
                        + "in the root settings.gradle. Update btrace-gradle-plugin/build.gradle to "
                        + "match, or update the catalog if the plugin is intentionally pinned.");
    }

    @Test
    @DisplayName("every ASM artifact in the plugin shares one version")
    void allAsmArtifactsShareOneVersion() throws IOException {
        String pluginBuild = read(pluginBuildFile());
        Matcher m = PLUGIN_ASM_COORDINATE.matcher(pluginBuild);
        Set<String> artifacts = new LinkedHashSet<>();
        Set<String> versions = new LinkedHashSet<>();
        while (m.find()) {
            artifacts.add(m.group(1));
            versions.add(m.group(2));
        }
        assertTrue(artifacts.contains("asm"), "expected an org.ow2.asm:asm dependency");
        assertTrue(artifacts.contains("asm-tree"), "expected an org.ow2.asm:asm-tree dependency");
        assertEquals(1, versions.size(), "ASM artifacts must share a single version: " + versions);
    }

    private static String catalogAsmVersion(String settings) {
        Matcher m = CATALOG_ASM_VERSION.matcher(settings);
        assertTrue(m.find(), "version('asm', ...) not found in the root settings.gradle");
        return m.group(1);
    }

    private static Set<String> pluginAsmVersions(String buildFile) {
        Matcher m = PLUGIN_ASM_COORDINATE.matcher(buildFile);
        Set<String> versions = new LinkedHashSet<>();
        while (m.find()) {
            versions.add(m.group(2));
        }
        return versions;
    }

    private static Path pluginBuildFile() {
        Path buildFile = requiredPath("btrace.test.pluginBuildFile");
        assertTrue(Files.isRegularFile(buildFile), "cannot locate btrace-gradle-plugin/build.gradle");
        return buildFile;
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        assertTrue(
                value != null && !value.isEmpty(),
                "system property " + property + " is not set; it is wired up by the test task");
        return Paths.get(value);
    }

    private static String read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return String.join("\n", lines);
    }
}
