package io.btrace.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Negative and positive tests for {@code validateServiceApis}. Each negative test builds a
 * throw-away extension project whose service interface breaks exactly one rule and asserts that the
 * build fails with that rule's message and no other error.
 */
class ExtensionServiceApiValidationTest {

    private static final String SERVICE = "com.example.api.PublicService";
    private static final String ONE_ERROR = "BTrace extension API validation failed with 1 error(s)";

    @TempDir Path projectDir;

    @BeforeEach
    void setUp() throws IOException {
        write(
                "settings.gradle",
                "rootProject.name = 'service-api-validation-test'\n"
                        + "include 'btrace-core', 'btrace-extension-processor', 'ext'\n");
        write("build.gradle", "allprojects { repositories { mavenCentral() } }\n");
        writeShadowStub();
        writeCoreStub();
        write(
                "btrace-extension-processor/build.gradle",
                "plugins { id 'java-library' }\ngroup = 'io.btrace'\nversion = '1.0'\n");
        write(
                "btrace-extension-processor/src/main/java/io/btrace/processor/Placeholder.java",
                "package io.btrace.processor;\npublic final class Placeholder {}\n");
    }

    // ---- positive ----

    @Test
    @DisplayName("a compliant service interface passes validation")
    void compliantServicePasses() throws IOException {
        project(SERVICE, "", compliantService(), true);

        BuildResult result = runner(":ext:validateServiceApis").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:validateServiceApis").getOutcome());
        assertTrue(result.getOutput().contains("validateServiceApis: OK for 1 service(s)"));
    }

    @Test
    @DisplayName("annotated permissions covered by requiredPermissions pass")
    void coveredPermissionsPass() throws IOException {
        project(
                SERVICE,
                "requiredPermissions = ['NONE', 'MEMORY_INFO']\n",
                "package com.example.api;\n"
                        + "import io.btrace.core.extensions.Permission;\n"
                        + "import io.btrace.core.extensions.ServiceDescriptor;\n"
                        + "@ServiceDescriptor(permissions = {Permission.MEMORY_INFO})\n"
                        + "public interface PublicService {\n"
                        + "  void inc(@NonNull String name);\n"
                        + "}\n",
                true);

        BuildResult result = runner(":ext:validateServiceApis").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:validateServiceApis").getOutcome());
    }

    // ---- negative: errors today ----

    @Test
    @DisplayName("a class instead of an interface is rejected")
    void serviceMustBeInterface() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public abstract class PublicService {\n"
                        + "  protected PublicService() {}\n"
                        + "  public abstract @NonNull String name();\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "must be a public top-level interface");
    }

    @Test
    @DisplayName("a package-private interface is rejected")
    void serviceMustBePublic() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\ninterface PublicService {\n  @NonNull String name();\n}\n",
                true);

        assertFailsWithOneError(":ext:validateServiceApis", "must be public");
    }

    @Test
    @DisplayName("a nested interface is rejected")
    void serviceMustBeTopLevel() throws IOException {
        write(
                "ext/src/main/java/com/example/api/Outer.java",
                "package com.example.api;\n"
                        + "public final class Outer {\n"
                        + "  private Outer() {}\n"
                        + "  public interface PublicService {\n"
                        + "    @NonNull String name();\n"
                        + "  }\n"
                        + "}\n");
        project("com.example.api.Outer$PublicService", "", null, true);

        assertFailsWithOneError(":ext:validateServiceApis", "must be top-level (not inner)");
    }

    @Test
    @DisplayName("a default method is rejected")
    void defaultMethodRejected() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull default String name() { return \"x\"; }\n"
                        + "}\n",
                true);

        assertFailsWithOneError(":ext:validateServiceApis", "Default method");
    }

    @Test
    @DisplayName("a checked exception in a throws clause is rejected")
    void checkedExceptionRejected() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull String name() throws Exception;\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "declares checked exception: java.lang.Exception");
    }

    @ParameterizedTest(name = "{0} in a signature is rejected")
    @CsvSource({
        "java.io.File",
        "java.net.URL",
        "java.nio.channels.Channel",
        "java.lang.reflect.Method"
    })
    void forbiddenSignatureTypeRejected(String type) throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  void put(@NonNull "
                        + type
                        + " arg);\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "uses forbidden type in signature: " + type);
    }

    @Test
    @DisplayName("an implementation type in a signature is rejected")
    void implementationTypeInSignatureRejected() throws IOException {
        write(
                "ext/src/main/java/com/example/impl/InternalMeter.java",
                "package com.example.impl;\npublic final class InternalMeter {}\n");
        // A type referenced from a service is exported with it, so the leak check only has
        // something to find when the type is explicitly excluded from the exports.
        project(
                SERVICE,
                "excludedExports = ['com.example.impl.InternalMeter']\n",
                "package com.example.api;\n"
                        + "import com.example.impl.InternalMeter;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull InternalMeter meter();\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis",
                "leaks implementation type in signature: com.example.impl.InternalMeter");
    }

    @Test
    @DisplayName("an interface return without @Nullable is rejected (shimability)")
    void interfaceReturnRequiresNullable() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull Meter meter();\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "must be @Nullable for shim compatibility");
    }

    @Test
    @DisplayName("two package-level @ExtensionDescriptor annotations are rejected")
    void secondExtensionDescriptorRejected() throws IOException {
        String descriptor =
                "@io.btrace.core.extensions.ExtensionDescriptor(name = \"test.ext\", version = \"1.0\")\n";
        write(
                "ext/src/main/java/com/example/api/package-info.java",
                descriptor + "package com.example.api;\n");
        write(
                "ext/src/main/java/com/example/other/package-info.java",
                descriptor + "package com.example.other;\n");
        project(SERVICE, "", compliantService(), true);

        BuildResult result = runner(":ext:validateServiceApis").buildAndFail();

        assertTrue(
                result.getOutput().contains("Found 2 package-level @ExtensionDescriptor"),
                result.getOutput());
    }

    @Test
    @DisplayName("annotated permissions not covered while scanning is off are rejected")
    void uncoveredPermissionRejected() throws IOException {
        project(SERVICE, "", permissionedService(), true);

        assertFailsWithOneError(
                ":ext:validateServiceApis",
                "requires permissions [MEMORY_INFO] not covered by plugin requiredPermissions");
    }

    @Test
    @DisplayName("the API jar build rejects a manifest that misses annotated permissions")
    void apiJarRejectsUncoveredPermission() throws IOException {
        project(SERVICE, "", permissionedService(), true);

        BuildResult result =
                runner(":ext:buildApiJar", "-x", "validateServiceApis").buildAndFail();

        assertTrue(
                result.getOutput()
                        .contains("Manifest permissions missing annotated requirements: [MEMORY_INFO]"),
                result.getOutput());
    }

    @Test
    @DisplayName("a declared service with no implementation is rejected")
    void serviceWithoutImplementationRejected() throws IOException {
        project(SERVICE, "", compliantService(), false);

        BuildResult result = runner(":ext:validateServiceApis").buildAndFail();

        assertTrue(
                result.getOutput().contains("No implementation found for declared service(s)"),
                result.getOutput());
    }

    @Test
    @DisplayName("a declared service that is not in the compiled output is rejected")
    void missingServiceRejected() throws IOException {
        // GhostImpl satisfies the implementation check so the failure comes from the lookup.
        write(
                "ext/src/main/java/com/example/api/GhostImpl.java",
                "package com.example.api;\nfinal class GhostImpl {}\n");
        project("com.example.api.Ghost", "", compliantService(), true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "not found in the exported API output");
    }

    @Test
    @DisplayName("an exported class with a public constructor is rejected")
    void publicConstructorInApiRejected() throws IOException {
        write(
                "ext/src/main/java/com/example/api/Value.java",
                "package com.example.api;\npublic class Value {\n  public Value() {}\n}\n");
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull Value value();\n"
                        + "}\n",
                true);

        BuildResult result = runner(":ext:validateServiceApis").buildAndFail();

        assertTrue(
                result.getOutput().contains("API classes with public constructors"),
                result.getOutput());
    }

    @Test
    @DisplayName("parameter nullability is an error when nullabilitySeverity is error")
    void parameterNullabilityErrorWhenConfigured() throws IOException {
        project(
                SERVICE,
                "nullabilitySeverity = 'error'\n",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  void inc(String name);\n"
                        + "}\n",
                true);

        assertFailsWithOneError(
                ":ext:validateServiceApis", "Missing nullability on parameter 0");
    }

    // ---- current behaviour that differs from docs/ExtensionInterfaceRules.md ----
    // The rules page calls nullability and shimability "required". These tests pin what the
    // validator does today, so a change of that behaviour is a deliberate, visible decision.

    @Test
    @DisplayName("a missing return nullability annotation is currently only a warning")
    void missingReturnNullabilityIsCurrentlyAWarning() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  String name();\n"
                        + "}\n",
                true);

        BuildResult result = runner(":ext:validateServiceApis").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:validateServiceApis").getOutcome());
        assertTrue(result.getOutput().contains("Missing nullability on return of"));
    }

    @Test
    @DisplayName("a missing parameter nullability annotation is currently only a warning")
    void missingParameterNullabilityIsCurrentlyAWarning() throws IOException {
        project(
                SERVICE,
                "",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  void inc(String name);\n"
                        + "}\n",
                true);

        BuildResult result = runner(":ext:validateServiceApis").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:validateServiceApis").getOutcome());
        assertTrue(result.getOutput().contains("Missing nullability on parameter 0"));
    }

    @Test
    @DisplayName("shimabilitySeverity = warn downgrades the @Nullable requirement")
    void shimabilityCanBeDowngradedToWarning() throws IOException {
        project(
                SERVICE,
                "shimabilitySeverity = 'warn'\n",
                "package com.example.api;\n"
                        + "public interface PublicService {\n"
                        + "  @NonNull Meter meter();\n"
                        + "}\n",
                true);

        BuildResult result = runner(":ext:validateServiceApis").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":ext:validateServiceApis").getOutcome());
        assertTrue(result.getOutput().contains("must be @Nullable for shim compatibility"));
    }

    // ---- helpers ----

    private static String compliantService() {
        return "package com.example.api;\n"
                + "public interface PublicService {\n"
                + "  void inc(@NonNull String name);\n"
                + "  @NonNull String name(@NonNull String key);\n"
                + "  @Nullable Meter meter(@NonNull String name);\n"
                + "}\n";
    }

    private static String permissionedService() {
        return "package com.example.api;\n"
                + "import io.btrace.core.extensions.Permission;\n"
                + "import io.btrace.core.extensions.ServiceDescriptor;\n"
                + "@ServiceDescriptor(permissions = {Permission.MEMORY_INFO})\n"
                + "public interface PublicService {\n"
                + "  void inc(@NonNull String name);\n"
                + "}\n";
    }

    private void assertFailsWithOneError(String task, String expectedMessage) {
        BuildResult result = runner(task).buildAndFail();
        String out = result.getOutput();
        assertTrue(out.contains(expectedMessage), "expected '" + expectedMessage + "' in:\n" + out);
        assertTrue(out.contains(ONE_ERROR), "expected exactly one error in:\n" + out);
    }

    /**
     * Writes the extension project.
     *
     * @param service fully qualified service name declared in {@code btraceExtension.services}
     * @param extraConfig extra lines for the {@code btraceExtension} block
     * @param serviceSource source of {@code com.example.api.PublicService}, or null if the caller
     *     wrote the service itself
     * @param withImplementation whether a ServiceLoader provider file names an existing class
     */
    private void project(
            String service, String extraConfig, String serviceSource, boolean withImplementation)
            throws IOException {
        write(
                "ext/build.gradle",
                "plugins {\n"
                        + "  id 'java-library'\n"
                        + "  id 'io.btrace.extension'\n"
                        + "  id 'com.gradleup.shadow'\n"
                        + "}\n"
                        + "group = 'com.example'\n"
                        + "version = '1.0'\n"
                        + "dependencies { implementation project(':btrace-core') }\n"
                        + "btraceExtension {\n"
                        + "  id = 'test.ext'\n"
                        + "  name = 'Test Extension'\n"
                        + "  description = 'Service API validation fixture'\n"
                        + "  services = ['"
                        + service
                        + "']\n"
                        + "  scanPermissions = false\n"
                        + "  requiredPermissions = ['NONE']\n"
                        + "  nullableAnnotations = ['com.example.api.Nullable']\n"
                        + "  nonnullAnnotations = ['com.example.api.NonNull']\n"
                        + extraConfig
                        + "}\n");
        String api = "ext/src/main/java/com/example/api/";
        write(
                api + "Nullable.java",
                "package com.example.api;\n"
                        + "import java.lang.annotation.*;\n"
                        + "@Retention(RetentionPolicy.CLASS)\n"
                        + "@Target({ElementType.METHOD, ElementType.PARAMETER})\n"
                        + "public @interface Nullable {}\n");
        write(
                api + "NonNull.java",
                "package com.example.api;\n"
                        + "import java.lang.annotation.*;\n"
                        + "@Retention(RetentionPolicy.CLASS)\n"
                        + "@Target({ElementType.METHOD, ElementType.PARAMETER})\n"
                        + "public @interface NonNull {}\n");
        write(api + "Meter.java", "package com.example.api;\npublic interface Meter {\n  void mark();\n}\n");
        if (serviceSource != null) {
            write(api + "PublicService.java", serviceSource);
        }
        write(
                "ext/src/main/java/com/example/impl/Marker.java",
                "package com.example.impl;\npublic final class Marker {}\n");
        if (withImplementation) {
            write("ext/src/main/resources/META-INF/services/" + service, "com.example.impl.Marker\n");
        }
    }

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args)
                .withDebug(true)
                .forwardOutput();
    }

    private void write(String relative, String content) throws IOException {
        Path path = projectDir.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void writeCoreStub() throws IOException {
        write(
                "btrace-core/build.gradle",
                "plugins { id 'java-library' }\ngroup = 'io.btrace'\nversion = '1.0'\n");
        String dir = "btrace-core/src/main/java/io/btrace/core/extensions/";
        write(
                dir + "ServiceDescriptor.java",
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.*;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target({ElementType.TYPE, ElementType.METHOD})\n"
                        + "public @interface ServiceDescriptor {\n"
                        + "  Permission[] permissions() default {};\n"
                        + "}\n");
        write(
                dir + "ExtensionDescriptor.java",
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.*;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.PACKAGE)\n"
                        + "public @interface ExtensionDescriptor {\n"
                        + "  String name();\n"
                        + "  String version();\n"
                        + "  String description() default \"\";\n"
                        + "  Permission[] permissions() default {};\n"
                        + "}\n");
        write(
                dir + "Permission.java",
                "package io.btrace.core.extensions;\n"
                        + "public enum Permission { NONE, MEMORY_INFO, THREAD_INFO }\n");
        // The plugin passes this processor to javac, so it has to exist even though no fixture
        // uses it.
        write(
                dir + "ExternalType.java",
                "package io.btrace.core.extensions;\n"
                        + "import java.lang.annotation.*;\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.TYPE)\n"
                        + "public @interface ExternalType {\n"
                        + "  String value();\n"
                        + "}\n");
        write(
                "btrace-core/src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java",
                "package io.btrace.extension.processor;\n"
                        + "import java.util.Set;\n"
                        + "import javax.annotation.processing.AbstractProcessor;\n"
                        + "import javax.annotation.processing.RoundEnvironment;\n"
                        + "import javax.annotation.processing.SupportedAnnotationTypes;\n"
                        + "import javax.lang.model.SourceVersion;\n"
                        + "import javax.lang.model.element.TypeElement;\n"
                        + "@SupportedAnnotationTypes(\"io.btrace.core.extensions.ExternalType\")\n"
                        + "public final class ExternalTypeProcessor extends AbstractProcessor {\n"
                        + "  @Override public SourceVersion getSupportedSourceVersion() {\n"
                        + "    return SourceVersion.latestSupported();\n"
                        + "  }\n"
                        + "  @Override public boolean process(Set<? extends TypeElement> a,\n"
                        + "      RoundEnvironment round) {\n"
                        + "    return false;\n"
                        + "  }\n"
                        + "}\n");
    }

    private void writeShadowStub() throws IOException {
        String pkg = "buildSrc/src/main/groovy/com/github/jengelman/gradle/plugins/shadow/";
        write(
                "buildSrc/build.gradle",
                "plugins { id 'groovy-gradle-plugin' }\nrepositories { mavenCentral() }\n");
        write(
                pkg + "ShadowPlugin.groovy",
                "package com.github.jengelman.gradle.plugins.shadow\n"
                        + "import org.gradle.api.Plugin\n"
                        + "import org.gradle.api.Project\n"
                        + "class ShadowPlugin implements Plugin<Project> {\n"
                        + "  void apply(Project project) {\n"
                        + "    project.tasks.register('shadowJar', ShadowJar)\n"
                        + "  }\n"
                        + "}\n");
        write(
                pkg + "ShadowJar.groovy",
                "package com.github.jengelman.gradle.plugins.shadow\n"
                        + "import org.gradle.api.tasks.Internal\n"
                        + "import org.gradle.api.tasks.bundling.Jar\n"
                        + "abstract class ShadowJar extends Jar {\n"
                        + "  @Internal Object configurations\n"
                        + "  void relocate(String from, String to) {}\n"
                        + "  void minimize() {}\n"
                        + "}\n");
        String props = "buildSrc/src/main/resources/META-INF/gradle-plugins/";
        String impl =
                "implementation-class=com.github.jengelman.gradle.plugins.shadow.ShadowPlugin\n";
        write(props + "com.github.johnrengelman.shadow.properties", impl);
        write(props + "com.gradleup.shadow.properties", impl);
    }
}
