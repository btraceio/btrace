package io.btrace.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
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
            assertEquals(
                    "3.0+",
                    jar.getManifest().getMainAttributes().getValue("BTrace-API-Version"),
                    "API manifest should advertise the 3.0 API baseline");
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
    @DisplayName("3.0 descriptors discover services and permissions without explicit configuration")
    void descriptorsDiscoverServicesAndPermissions() throws IOException {
        writeExtensionProject("", false);

        BuildResult result = createRunner().withArguments(":ext:buildApiJar").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:buildApiJar").getOutcome());
        File apiJar = projectDir.resolve("ext/build/libs/ext-1.0-api.jar").toFile();
        try (JarFile jar = new JarFile(apiJar)) {
            String services =
                    jar.getManifest()
                            .getMainAttributes()
                            .getValue("BTrace-Extension-Services");
            String permissions =
                    jar.getManifest()
                            .getMainAttributes()
                            .getValue("BTrace-Extension-Permissions");
            assertEquals("com.example.api.PublicService", services);
            assertEquals(Set.of("MEMORY_INFO", "THREAD_INFO"), Set.of(permissions.split(",")));
            assertFalse(services.contains("com.example.impl.HiddenInternal"));
            assertFalse(services.contains("com.example.api.PublicValue"));
            assertNotNull(jar.getEntry("com/example/api/PublicService.class"));
            assertNotNull(jar.getEntry("com/example/api/PublicValue.class"));
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .equals(
                                                            "com/example/impl/HiddenInternal.class")));
        }
    }

    @Test
    @DisplayName("external builds use the explicitly pinned public BTrace distribution")
    void externalBuildUsesPinnedBTraceDistribution() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'external-extension'\n");
        writeFile(
                rootBuildFile,
                "plugins { id 'io.btrace.extension' }\n"
                        + "version = '9.9.9'\n"
                        + "btraceExtension { btraceVersion = '3.0.0' }\n"
                        + "tasks.register('printProcessor') { doLast {\n"
                        + "  configurations.annotationProcessor.dependencies.each { println \"PROCESSOR=${it.group}:${it.name}:${it.version}\" }\n"
                        + "} }\n");

        BuildResult result = createRunner().withArguments("printProcessor").build();

        assertTrue(result.getOutput().contains("PROCESSOR=io.btrace:btrace:3.0.0"));
        assertFalse(result.getOutput().contains("btrace-core"));
    }

    @Test
    @DisplayName("external builds require an explicit BTrace version without a published plugin JAR")
    void externalBuildWithoutBTraceVersionFailsClearly() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'external-extension'\n");
        writeFile(rootBuildFile, "plugins { id 'io.btrace.extension' }\n");

        BuildResult result = createRunner().withArguments("tasks").buildAndFail();

        assertTrue(result.getOutput().contains("btraceExtension.btraceVersion"));
        assertTrue(result.getOutput().contains("concrete BTrace release version"));
    }

    @Test
    @DisplayName("the @ExternalType processor is passed explicitly, not discovered from a service entry")
    void externalTypeProcessorIsPassedExplicitly() throws IOException {
        writeExtensionProject(
                "tasks.register('printCompilerArgs') { doLast {\n"
                        + "  println \"COMPILER_ARGS=${compileJava.options.compilerArgs}\"\n"
                        + "} }\n");
        writeFile(
                projectDir.resolve("ext/src/main/java/com/example/api/AppEvent.java"),
                "package com.example.api;\n"
                        + "@io.btrace.core.extensions.ExternalType(\"java.lang.String\")\n"
                        + "public interface AppEvent {}\n");

        BuildResult result =
                createRunner().withArguments(":ext:compileJava", ":ext:printCompilerArgs").build();

        assertTrue(
                result.getOutput()
                        .contains("-processor, io.btrace.extension.processor.ExternalTypeProcessor"),
                "compileJava should name the processor explicitly:\n" + result.getOutput());
        assertTrue(
                projectDir.resolve("ext/build/classes/java/main/com/example/api/AppEvent$Ext.class")
                        .toFile()
                        .isFile(),
                "the $Ext adapter should be generated");
    }

    @Test
    @DisplayName("generateServiceShims regenerates shims when a service interface changes")
    void shimsAreRegeneratedWhenServiceInterfaceChanges() throws IOException {
        writeExtensionProject();

        BuildResult first = createRunner().withArguments(":ext:compileServiceShims").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":ext:compileServiceShims").getOutcome());

        // Add a method to the exported service: the generated shims must follow, or the second
        // build fails with "is not abstract and does not override abstract method".
        writeFile(
                projectDir.resolve("ext/src/main/java/com/example/api/PublicService.java"),
                "package com.example.api;\n"
                        + "import io.btrace.core.extensions.Permission;\n"
                        + "import io.btrace.core.extensions.ServiceDescriptor;\n"
                        + "@ServiceDescriptor(permissions = {Permission.MEMORY_INFO})\n"
                        + "public interface PublicService {\n"
                        + "  PublicValue value();\n"
                        + "  String name();\n"
                        + "}\n");
        writeFile(
                projectDir.resolve("ext/src/main/java/com/example/impl/PublicServiceImpl.java"),
                "package com.example.impl;\n"
                        + "import com.example.api.PublicService;\n"
                        + "import com.example.api.PublicValue;\n"
                        + "public class PublicServiceImpl implements PublicService {\n"
                        + "  private final HiddenSupport support = new HiddenSupport();\n"
                        + "  public PublicValue value() {\n"
                        + "    return new PublicValue(support.message());\n"
                        + "  }\n"
                        + "  public String name() {\n"
                        + "    return \"renamed\";\n"
                        + "  }\n"
                        + "}\n");

        BuildResult second = createRunner().withArguments(":ext:compileServiceShims").build();
        assertEquals(
                TaskOutcome.SUCCESS,
                second.task(":ext:generateServiceShims").getOutcome(),
                "shim generation must rerun when the authored classes change");
        String shim =
                Files.readString(
                        projectDir.resolve(
                                "ext/build/generated/sources/btraceShims/api/com/example/api/btrace/shim/NoOpPublicService.java"),
                        StandardCharsets.UTF_8);
        assertTrue(shim.contains("name()"), "regenerated shim should implement the new method");
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
    @DisplayName("the Maven publication has the API jar as its main artifact plus sources, javadoc and the extension zip")
    void publicationIsCentralShaped() throws IOException {
        writeExtensionProject(
                // The fixture's PublicValue has a public constructor; not what this test is about
                "btraceExtension { apiCtorSeverity = 'off' }\n"
                        + "apply plugin: 'signing'\n"
                        // The Central metadata and signing snippet from BTraceExtensionDevelopmentGuide.md
                        + "publishing {\n"
                        + "  publications.named('mavenExtension') {\n"
                        + "    pom {\n"
                        + "      url = 'https://github.com/example-org/my-extension'\n"
                        + "      licenses { license { name = 'The Apache License, Version 2.0'; url = 'https://www.apache.org/licenses/LICENSE-2.0.txt' } }\n"
                        + "      developers { developer { id = 'me'; name = 'My Name' } }\n"
                        + "      scm { url = 'https://github.com/example-org/my-extension'; connection = 'scm:git:https://github.com/example-org/my-extension.git' }\n"
                        + "    }\n"
                        + "  }\n"
                        + "  repositories {\n"
                        + "    maven { name = 'fixture'; url = layout.buildDirectory.dir('repo') }\n"
                        + "  }\n"
                        + "}\n"
                        + "signing {\n"
                        // No key in the test; the guide's builds sign for real
                        + "  required = false\n"
                        + "  sign publishing.publications.mavenExtension\n"
                        + "}\n");
        // Packaging validates that each service has an implementation it can locate
        writeFile(
                projectDir.resolve(
                        "ext/src/main/resources/META-INF/services/com.example.api.PublicService"),
                "com.example.impl.PublicServiceImpl\n");

        BuildResult result = createRunner()
                .withArguments(":ext:publishMavenExtensionPublicationToFixtureRepository")
                .build();
        assertEquals(
                TaskOutcome.SUCCESS,
                result.task(":ext:publishMavenExtensionPublicationToFixtureRepository").getOutcome());

        Path dir = projectDir.resolve("ext/build/repo/com/example/ext/1.0");
        File mainJar = dir.resolve("ext-1.0.jar").toFile();
        assertTrue(
                mainJar.isFile(),
                () -> "main artifact should be published without a classifier; published: " + listFiles(projectDir.resolve("ext/build/repo")));
        try (JarFile jar = new JarFile(mainJar)) {
            assertNotNull(jar.getEntry("com/example/api/PublicService.class"), "main jar is the API jar");
            assertNull(jar.getEntry("com/example/impl/PublicServiceImpl.class"), "no impl classes in the main jar");
        }
        try (JarFile jar = new JarFile(dir.resolve("ext-1.0-sources.jar").toFile())) {
            assertNotNull(jar.getEntry("com/example/api/PublicService.java"));
        }
        try (JarFile jar = new JarFile(dir.resolve("ext-1.0-javadoc.jar").toFile())) {
            assertNotNull(jar.getEntry("com/example/api/PublicService.html"));
        }
        try (ZipFile zip = new ZipFile(dir.resolve("ext-1.0-extension.zip").toFile())) {
            assertNotNull(zip.getEntry("ext-1.0-api.jar"), "extension zip keeps the installable layout");
            assertNotNull(zip.getEntry("ext-1.0-impl.jar"), "extension zip keeps the installable layout");
        }
        assertFalse(dir.resolve("ext-1.0-api.jar").toFile().exists(), "no duplicate classified API jar");

        String pom = new String(Files.readAllBytes(dir.resolve("ext-1.0.pom")), StandardCharsets.UTF_8);
        // Absent <packaging> means jar; "pom" packaging would hide the artifacts from consumers
        assertFalse(pom.contains("<packaging>"), pom);
        assertTrue(pom.contains("<name>Test Extension</name>"), pom);
        assertTrue(pom.contains("<description>Fixture for extension plugin tests</description>"), pom);
        assertTrue(pom.contains("<url>https://github.com/example-org/my-extension</url>"), pom);
        assertTrue(pom.contains("<licenses>") && pom.contains("<developers>") && pom.contains("<scm>"), pom);
    }

    private static String listFiles(Path root) {
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(f -> root.relativize(f).toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
        } catch (IOException e) {
            return "<" + e + ">";
        }
    }

    private void writeStubCoreProject() throws IOException {
        Path dir = projectDir.resolve("btrace-core");
        Files.createDirectories(dir.resolve("src/main/java/io/btrace/core/extensions"));
        Files.createDirectories(dir.resolve("src/main/java/io/btrace/extension/processor"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins { id 'java-library' }\n"
                        + "group = 'io.btrace'\n"
                        + "version = '1.0'\n");
        writeFile(
                dir.resolve(
                        "src/main/java/io/btrace/core/extensions/ServiceDescriptor.java"),
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target({ElementType.TYPE, ElementType.METHOD})\n"
                        + "public @interface ServiceDescriptor {\n"
                        + "  Permission[] permissions() default {};\n"
                        + "}\n");
        writeFile(
                dir.resolve(
                        "src/main/java/io/btrace/core/extensions/ExtensionDescriptor.java"),
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.PACKAGE)\n"
                        + "public @interface ExtensionDescriptor {\n"
                        + "  String name();\n"
                        + "  String version();\n"
                        + "  String description() default \"\";\n"
                        + "  Permission[] permissions() default {};\n"
                        + "}\n");
        writeFile(
                dir.resolve("src/main/java/io/btrace/core/extensions/Permission.java"),
                "package io.btrace.core.extensions;\n"
                        + "public enum Permission { NONE, MEMORY_INFO, THREAD_INFO }\n");
        writeFile(
                dir.resolve(
                        "src/main/java/io/btrace/core/extensions/ExternalType.java"),
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
        writeFile(
                dir.resolve(
                        "src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java"),
                "package io.btrace.extension.processor;\n"
                        + "import java.io.Writer;\n"
                        + "import java.util.Set;\n"
                        + "import javax.annotation.processing.AbstractProcessor;\n"
                        + "import javax.annotation.processing.RoundEnvironment;\n"
                        + "import javax.annotation.processing.SupportedAnnotationTypes;\n"
                        + "import javax.lang.model.SourceVersion;\n"
                        + "import javax.lang.model.element.Element;\n"
                        + "import javax.lang.model.element.TypeElement;\n"
                        + "@SupportedAnnotationTypes(\"io.btrace.core.extensions.ExternalType\")\n"
                        + "public final class ExternalTypeProcessor extends AbstractProcessor {\n"
                        + "  @Override public SourceVersion getSupportedSourceVersion() {\n"
                        + "    return SourceVersion.latestSupported();\n"
                        + "  }\n"
                        + "  @Override public boolean process(Set<? extends TypeElement> annotations,\n"
                        + "      RoundEnvironment round) {\n"
                        + "    for (TypeElement annotation : annotations) {\n"
                        + "      for (Element e : round.getElementsAnnotatedWith(annotation)) {\n"
                        + "        String pkg = processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString();\n"
                        + "        String name = e.getSimpleName() + \"$Ext\";\n"
                        + "        try (Writer w = processingEnv.getFiler()\n"
                        + "            .createSourceFile(pkg + \".\" + name, e).openWriter()) {\n"
                        + "          w.write(\"package \" + pkg + \"; public final class \" + name + \" {}\");\n"
                        + "        } catch (java.io.IOException ex) {\n"
                        + "          throw new IllegalStateException(ex);\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "    return true;\n"
                        + "  }\n"
                        + "}\n");
    }

    private void writeStubProcessorProject() throws IOException {
        Path dir = projectDir.resolve("btrace-extension-processor");
        Files.createDirectories(dir.resolve("src/main/java/io/btrace/processor"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins { id 'java-library' }\n"
                        + "group = 'io.btrace'\n"
                        + "version = '1.0'\n");
        writeFile(
                dir.resolve(
                        "src/main/java/io/btrace/processor/PlaceholderProcessor.java"),
                "package io.btrace.processor;\n"
                        + "public final class PlaceholderProcessor {}\n");
    }

    private void writeExtensionProject() throws IOException {
        writeExtensionProject("");
    }

    private void writeExtensionProject(String extraBuildLogic) throws IOException {
        writeExtensionProject(extraBuildLogic, true);
    }

    private void writeExtensionProject(String extraBuildLogic, boolean explicitServices)
            throws IOException {
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
                        + (explicitServices
                                ? "  services = ['com.example.api.PublicService']\n"
                                        + "  requiredPermissions = ['NONE']\n"
                                        + "  scanPermissions = false\n"
                                : "")
                        + "}\n"
                        + extraBuildLogic);
        if (!explicitServices) {
            writeFile(
                    dir.resolve("src/main/java/com/example/api/package-info.java"),
                    "@io.btrace.core.extensions.ExtensionDescriptor(\n"
                            + "    name = \"test.ext\",\n"
                            + "    version = \"1.0\",\n"
                            + "    permissions = {io.btrace.core.extensions.Permission.THREAD_INFO})\n"
                            + "package com.example.api;\n");
        }
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
                        + (explicitServices
                                ? ""
                                : "import io.btrace.core.extensions.Permission;\n"
                                        + "import io.btrace.core.extensions.ServiceDescriptor;\n"
                                        + "@ServiceDescriptor(permissions = {Permission.MEMORY_INFO})\n")
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
                        + "abstract class ShadowJar extends Jar {\n"
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

    /**
     * Writes an extension project that does not apply Shadow itself, so auto-apply decides.
     *
     * @param autoApplyShadow value for the opt-out, or null to leave it at the default
     */
    private void writeAutoApplyProject(Boolean autoApplyShadow) throws IOException {
        Path dir = projectDir.resolve("ext");
        Files.createDirectories(dir.resolve("src/main/java/com/example/api"));
        writeFile(
                dir.resolve("build.gradle"),
                "plugins {\n"
                        + "  id 'java-library'\n"
                        + "  id 'io.btrace.extension'\n"
                        + "}\n"
                        + "group = 'com.example'\n"
                        + "version = '1.0'\n"
                        + "btraceExtension {\n"
                        + "  id = 'test.ext'\n"
                        + "  name = 'Test Extension'\n"
                        + (autoApplyShadow == null ? "" : "  autoApplyShadow = " + autoApplyShadow + "\n")
                        + "}\n");
    }

    @Test
    @DisplayName("autoApplyShadow = false suppresses the automatic Shadow apply")
    void autoApplyShadowFalseIsHonoured() throws IOException {
        // The flag is set from the btraceExtension block, which runs after the plugin is applied,
        // so honouring it requires the decision to be deferred past project evaluation.
        writeAutoApplyProject(false);

        // Opting out without applying Shadow yourself is expected to fail: the plugin needs it and
        // says so. That failure is the proof the opt-out was honoured - previously the flag was
        // read before the extension existed, so Shadow was applied regardless and this build
        // succeeded.
        BuildResult result = createRunner().withArguments(":ext:tasks").buildAndFail();

        assertTrue(
                result.getOutput().contains("Shadow auto-apply disabled"),
                "plugin should report the opt-out was honoured:\n" + result.getOutput());
        assertFalse(
                result.getOutput().contains("Applying Shadow plugin automatically"),
                "Shadow must not be auto-applied when the opt-out is set:\n" + result.getOutput());
        assertTrue(
                result.getOutput().contains("Shadow plugin is required"),
                "opting out should surface the documented guidance:\n" + result.getOutput());
    }

    @Test
    @DisplayName("Shadow is auto-applied when the opt-out is not set")
    void shadowIsAutoAppliedByDefault() throws IOException {
        writeAutoApplyProject(null);

        BuildResult result = createRunner().withArguments(":ext:tasks").build();

        assertTrue(
                result.getOutput().contains("Applying Shadow plugin automatically"),
                "Shadow should still be auto-applied by default:\n" + result.getOutput());
    }
}
