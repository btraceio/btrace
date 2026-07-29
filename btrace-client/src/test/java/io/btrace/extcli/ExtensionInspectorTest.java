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
package io.btrace.extcli;

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.ServiceDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionInspectorTest {

  @TempDir Path tempDir;

  @Test
  void inspectValidDirectory() throws IOException {
    Path extDir = tempDir.resolve("test-extension");
    TestExtensionBuilder.createExtensionDirectory("test-ext", "1.0.0", extDir, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "Extension should be valid");
    assertEquals("test-ext", report.id);
    assertEquals("1.0.0", report.version);
    assertFalse(report.privileged, "Extension should not be privileged");
  }

  @Test
  void inspectValidZip() throws IOException {
    Path zipFile = tempDir.resolve("test-extension.zip");
    TestExtensionBuilder.createExtensionZip("test-ext", "2.0.0", zipFile, false);

    ExtensionReport report = ExtensionInspector.inspect(zipFile);

    assertTrue(report.ok, "Extension ZIP should be valid");
    assertEquals("test-ext", report.id);
    assertEquals("2.0.0", report.version);
  }

  @Test
  void detectMissingApiJar() throws IOException {
    Path extDir = tempDir.resolve("incomplete-extension");
    Files.createDirectories(extDir);
    // Only create impl jar, no api jar
    Path implJar = extDir.resolve("test-1.0.0-impl.jar");
    TestExtensionBuilder.createImplJar("test", implJar);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertFalse(report.ok, "Extension should be invalid without API JAR");
    assertTrue(report.message.contains("Missing api/impl jars"));
  }

  @Test
  void detectMissingImplJar() throws IOException {
    Path extDir = tempDir.resolve("incomplete-extension");
    Files.createDirectories(extDir);
    // Only create api jar, no impl jar
    Path apiJar = extDir.resolve("test-1.0.0-api.jar");
    TestExtensionBuilder.createApiJar("test", "1.0.0", apiJar, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertFalse(report.ok, "Extension should be invalid without implementation JAR");
    assertTrue(report.message.contains("Missing api/impl jars"));
  }

  @Test
  void inspectExtensionWithPermissions() throws IOException {
    Path extDir = tempDir.resolve("permissions-extension");
    TestExtensionBuilder.createExtensionDirectory("perm-ext", "1.0.0", extDir, true);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "Extension with permissions should be valid");
    assertTrue(
        report.requiredPermNames.contains("NETWORK"),
        "declared permission should be reported: " + report.requiredPermNames);
    assertTrue(report.privileged, "NETWORK is a privileged permission");
  }

  @Test
  void servicePermissionsAreMergedFromServiceDescriptor() throws IOException {
    // The service interface is on the test classpath, so ExtensionInspector's classloader resolves
    // it through its parent and reads the annotation. This is the only remaining path by which an
    // annotation contributes to the reported permission set.
    Path extDir = tempDir.resolve("service-permissions-extension");
    TestExtensionBuilder.createExtensionDirectory(
        "svc-perm-ext", "1.0.0", extDir, false, AnnotatedFixtureService.class.getName());

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(
        report.requiredPermNames.contains("THREADS"),
        "@ServiceDescriptor permissions should be merged: " + report.requiredPermNames);
    assertTrue(report.privileged, "THREADS is a privileged permission");
  }

  @Test
  void unresolvableServiceClassDoesNotBreakInspection() throws IOException {
    Path extDir = tempDir.resolve("unresolvable-service-extension");
    TestExtensionBuilder.createExtensionDirectory(
        "missing-svc-ext", "1.0.0", extDir, false, "com.example.NotOnTheClasspath");

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "A service class that cannot be loaded must not fail inspection");
    assertEquals(Collections.singleton("com.example.NotOnTheClasspath"), report.services);
  }

  @Test
  void servicesComeFromTheManifestAttribute() throws IOException {
    Path extDir = tempDir.resolve("services-extension");
    TestExtensionBuilder.createExtensionDirectory(
        "svc-ext", "1.0.0", extDir, false, "com.example.FixtureService");

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "Extension should be valid");
    assertEquals(
        Collections.singleton("com.example.FixtureService"),
        report.services,
        "services must be read from the API JAR's BTrace-Extension-Services attribute");
  }

  @Test
  void serviceFilesInTheImplJarAreNotReportedAsServices() throws IOException {
    // createImplJar always writes META-INF/services/org.example.TestService, standing in for the
    // third-party SPI files that shading leaves in a real extension's impl JAR. Those are not the
    // extension's services and must never be reported as such.
    Path extDir = tempDir.resolve("decoy-extension");
    TestExtensionBuilder.createExtensionDirectory(
        "decoy-ext", "1.0.0", extDir, false, "com.example.FixtureService");

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertFalse(
        report.services.contains("org.example.TestService"),
        "impl JAR service files must not leak into the reported service list");
  }

  @Test
  void servicesAreEmptyWhenTheManifestDeclaresNone() throws IOException {
    Path extDir = tempDir.resolve("no-services-extension");
    TestExtensionBuilder.createExtensionDirectory("plain-ext", "1.0.0", extDir, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(
        report.services.isEmpty(),
        "no BTrace-Extension-Services attribute means no services, not whatever the impl JAR"
            + " happens to contain");
  }

  @Test
  void extractManifestId() throws IOException {
    Path extDir = tempDir.resolve("manifest-id-test");
    TestExtensionBuilder.createExtensionDirectory("manifest-ext", "1.5.0", extDir, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertEquals("manifest-ext", report.id, "Should extract ID from manifest");
  }

  /** Stands in for a real extension's service interface, which carries its permissions. */
  @ServiceDescriptor(permissions = {Permission.THREADS})
  public interface AnnotatedFixtureService {
    void doSomething();
  }
}
