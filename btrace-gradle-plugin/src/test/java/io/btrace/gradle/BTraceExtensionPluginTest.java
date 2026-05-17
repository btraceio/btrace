package io.btrace.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BTraceExtensionPluginTest {

    @TempDir
    Path projectDir;

    private Path settingsFile;
    private Path rootBuildFile;

    @BeforeEach
    void setUp() throws IOException {
        settingsFile = projectDir.resolve("settings.gradle");
        rootBuildFile = projectDir.resolve("build.gradle");
        writeFile(
                settingsFile,
                "rootProject.name = 'extension-plugin-test'\n"
                        + "include 'btrace-core', 'btrace-extension-processor', 'ext'\n");
        writeFile(rootBuildFile, "allprojects { repositories { mavenCentral() } }\n");
        writeBuildSrcShadowPlugin();
        writeStubCoreProject();
        writeStubProcessorProject();
    }

    @Test
    @DisplayName("buildApiJar keeps resources and excludes non-exported shim types")
    void buildApiJarKeepsResourcesAndExcludesInternalShims() throws IOException {
        writeExtensionProject();

        BuildResult result = createRunner()
                .withArguments(":ext:buildApiJar")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:buildApiJar").getOutcome());

        File apiJar = projectDir.resolve("ext/build/libs/ext-1.0-api.jar").toFile();
        assertTrue(apiJar.isFile(), "API jar should exist");
        try (JarFile jar = new JarFile(apiJar)) {
            assertNotNull(
                    jar.getEntry("META-INF/btrace/test-resource.txt"),
                    "API jar should keep main resources");
            assertNotNull(
                    jar.getEntry("com/example/api/PublicService.class"),
                    "Exported service should be present");
            assertNotNull(
                    jar.getEntry("com/example/api/btrace/shim/NoOpPublicService.class"),
                    "Shim for exported service should be present");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .contains(
                                                            "com/example/impl/btrace/shim/NoOpHiddenInternal")),
                    "API jar should not contain shims for non-exported interfaces");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .contains(
                                                            "com/example/impl/btrace/shim/ThrowingHiddenInternal")),
                    "API jar should not contain throwing shims for non-exported interfaces");
            String shimIndex =
                    new String(
                            jar.getInputStream(jar.getEntry("META-INF/btrace/shims.index"))
                                    .readAllBytes(),
                            StandardCharsets.UTF_8);
            assertTrue(
                    shimIndex.contains("com.example.api.PublicService"),
                    "Shim index should include exported services");
            assertFalse(
                    shimIndex.contains("com.example.impl.HiddenInternal"),
                    "Shim index should exclude non-exported interfaces");
        }
    }

    @Test
    @DisplayName("published API source and javadoc artifacts stay API-only")
    void apiPublicationsStayApiOnly() throws IOException {
        writeExtensionProject();

        BuildResult result = createRunner()
                .withArguments(":ext:apiSourcesJar", ":ext:apiJavadocJar")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:apiSourcesJar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:apiJavadocJar").getOutcome());

        File sourcesJar = projectDir.resolve("ext/build/libs/ext-1.0-api-sources.jar").toFile();
        assertTrue(sourcesJar.isFile(), "API sources jar should exist");
        try (JarFile jar = new JarFile(sourcesJar)) {
            assertNotNull(
                    jar.getEntry("com/example/api/PublicService.java"),
                    "Exported API sources should be present");
            assertNotNull(
                    jar.getEntry("META-INF/btrace/test-resource.txt"),
                    "API sources jar should include shared resources");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .equals("com/example/impl/HiddenSupport.java")),
                    "Implementation sources should not leak into api-sources");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .equals("com/example/impl/PublicServiceImpl.java")),
                    "Implementation classes should not leak into api-sources");
        }

        File javadocJar = projectDir.resolve("ext/build/libs/ext-1.0-api-javadoc.jar").toFile();
        assertTrue(javadocJar.isFile(), "API javadoc jar should exist");
        try (JarFile jar = new JarFile(javadocJar)) {
            assertNotNull(
                    jar.getEntry("com/example/api/PublicService.html"),
                    "Exported API javadocs should be present");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .equals("com/example/impl/HiddenSupport.html")),
                    "Implementation javadocs should not leak into api-javadoc");
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .equals("com/example/impl/PublicServiceImpl.html")),
                    "Implementation implementation javadocs should not leak into api-javadoc");
        }
    }

    @Test
    @DisplayName("updateRegistryCatalog writes entry into local registry checkout")
    void updateRegistryCatalogWritesLocalRegistry() throws IOException {
        Path registryDir = projectDir.resolve("registry-repo");
        Files.createDirectories(registryDir.resolve("registry"));
        writeFile(
                registryDir.resolve("registry/extensions.json"),
                "{\n"
                        + "  \"schema_version\": 1,\n"
                        + "  \"extensions\": []\n"
                        + "}\n");

        writeExtensionProject(
                "btraceRegistry {\n"
                        + "  prMode = 'off'\n"
                        + "  verifyPublishedCoordinates = false\n"
                        + "  registryWorktreeDir = file('"
                        + registryDir.toString().replace("\\", "/")
                        + "')\n"
                        + "  tags = ['metrics']\n"
                        + "}\n");

        BuildResult result = createRunner().withArguments(":ext:updateRegistryCatalog").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:updateRegistryCatalog").getOutcome());
        String json =
                Files.readString(
                        registryDir.resolve("registry/extensions.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"id\" : \"test.ext\""));
        assertTrue(json.contains("\"artifactId\" : \"ext\""));
        assertTrue(json.contains("\"version\" : \"1.0\""));
    }

    private void writeStubCoreProject() throws IOException {
        Path dir = projectDir.resolve("btrace-core");
        Files.createDirectories(dir.resolve("src/main/java/org/openjdk/btrace/core/extensions"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins { id 'java-library' }\n"
                        + "group = 'io.btrace'\n"
                        + "version = '1.0'\n");
        writeFile(
                dir.resolve(
                        "src/main/java/org/openjdk/btrace/core/extensions/ServiceDescriptor.java"),
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target({ElementType.TYPE, ElementType.METHOD})\n"
                        + "public @interface ServiceDescriptor {\n"
                        + "  String[] permissions() default {};\n"
                        + "}\n");
        writeFile(
                dir.resolve(
                        "src/main/java/org/openjdk/btrace/core/extensions/ExtensionDescriptor.java"),
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.PACKAGE)\n"
                        + "public @interface ExtensionDescriptor {\n"
                        + "  String[] permissions() default {};\n"
                        + "}\n");
        writeFile(
                dir.resolve(
                        "src/main/java/org/openjdk/btrace/core/extensions/ExternalType.java"),
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.TYPE)\n"
                        + "public @interface ExternalType {\n"
                        + "  String value();\n"
                        + "}\n");
    }

    private void writeStubProcessorProject() throws IOException {
        Path dir = projectDir.resolve("btrace-extension-processor");
        Files.createDirectories(dir.resolve("src/main/java/org/openjdk/btrace/processor"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins { id 'java-library' }\n"
                        + "group = 'io.btrace'\n"
                        + "version = '1.0'\n");
        writeFile(
                dir.resolve(
                        "src/main/java/org/openjdk/btrace/processor/PlaceholderProcessor.java"),
                "package io.btrace.processor;\n"
                        + "public final class PlaceholderProcessor {}\n");
    }

    private void writeExtensionProject() throws IOException {
        writeExtensionProject("");
    }

    private void writeExtensionProject(String extraBuildLogic) throws IOException {
        Path dir = projectDir.resolve("ext");
        Files.createDirectories(dir.resolve("src/main/java/com/example/api"));
        Files.createDirectories(dir.resolve("src/main/java/com/example/impl"));
        Files.createDirectories(dir.resolve("src/main/resources/META-INF/btrace"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins {\n"
                        + "  id 'java-library'\n"
                        + "  id 'io.btrace.extension'\n"
                        + "  id 'com.gradleup.shadow'\n"
                        + "}\n"
                        + "group = 'com.example'\n"
                        + "version = '1.0'\n"
                        + "dependencies {\n"
                        + "  implementation project(':btrace-core')\n"
                        + "}\n"
                        + "btraceExtension {\n"
                        + "  id = 'test.ext'\n"
                        + "  name = 'Test Extension'\n"
                        + "  description = 'Fixture for extension plugin tests'\n"
                        + "  services = ['com.example.api.PublicService']\n"
                        + "  requiredPermissions = ['NONE']\n"
                        + "  scanPermissions = false\n"
                        + "}\n"
                        + extraBuildLogic);
        writeFile(
                dir.resolve("src/main/java/com/example/api/PublicValue.java"),
                "package com.example.api;\n"
                        + "public class PublicValue {\n"
                        + "  public final String value;\n"
                        + "  public PublicValue(String value) {\n"
                        + "    this.value = value;\n"
                        + "  }\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/java/com/example/api/PublicService.java"),
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  PublicValue value();\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/java/com/example/impl/HiddenInternal.java"),
                "package com.example.impl;\n"
                        + "public interface HiddenInternal {\n"
                        + "  String secret();\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/java/com/example/impl/HiddenSupport.java"),
                "package com.example.impl;\n"
                        + "public class HiddenSupport {\n"
                        + "  public String message() {\n"
                        + "    return \"hidden\";\n"
                        + "  }\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/java/com/example/impl/PublicServiceImpl.java"),
                "package com.example.impl;\n"
                        + "import com.example.api.PublicService;\n"
                        + "import com.example.api.PublicValue;\n"
                        + "public class PublicServiceImpl implements PublicService {\n"
                        + "  private final HiddenSupport support = new HiddenSupport();\n"
                        + "  public PublicValue value() {\n"
                        + "    return new PublicValue(support.message());\n"
                        + "  }\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/resources/META-INF/btrace/test-resource.txt"),
                "api-resource\n");
    }

    private void writeBuildSrcShadowPlugin() throws IOException {
        Path dir =
                projectDir.resolve(
                        "buildSrc/src/main/groovy/com/github/jengelman/gradle/plugins/shadow");
        Files.createDirectories(dir);
        Files.createDirectories(projectDir.resolve("buildSrc/src/main/resources/META-INF/gradle-plugins"));
        writeFile(
                projectDir.resolve("buildSrc/build.gradle"),
                "plugins { id 'groovy-gradle-plugin' }\n"
                        + "repositories { mavenCentral() }\n");
        writeFile(
                dir.resolve("ShadowPlugin.groovy"),
                "package com.github.jengelman.gradle.plugins.shadow\n"
                        + "import org.gradle.api.Plugin\n"
                        + "import org.gradle.api.Project\n"
                        + "class ShadowPlugin implements Plugin<Project> {\n"
                        + "  void apply(Project project) {\n"
                        + "    project.tasks.register('shadowJar', ShadowJar)\n"
                        + "  }\n"
                        + "}\n");
        writeFile(
                dir.resolve("ShadowJar.groovy"),
                "package com.github.jengelman.gradle.plugins.shadow\n"
                        + "import org.gradle.api.tasks.Internal\n"
                        + "import org.gradle.api.tasks.bundling.Jar\n"
                        + "class ShadowJar extends Jar {\n"
                        + "  @Internal Object configurations\n"
                        + "  void relocate(String from, String to) {}\n"
                        + "  void minimize() {}\n"
                        + "}\n");
        writeFile(
                projectDir.resolve(
                        "buildSrc/src/main/resources/META-INF/gradle-plugins/com.github.johnrengelman.shadow.properties"),
                "implementation-class=com.github.jengelman.gradle.plugins.shadow.ShadowPlugin\n");
        writeFile(
                projectDir.resolve(
                        "buildSrc/src/main/resources/META-INF/gradle-plugins/com.gradleup.shadow.properties"),
                "implementation-class=com.github.jengelman.gradle.plugins.shadow.ShadowPlugin\n");
    }

    private GradleRunner createRunner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .forwardOutput();
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
