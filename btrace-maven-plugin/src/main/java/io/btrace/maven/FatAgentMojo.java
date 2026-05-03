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
package io.btrace.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Builds a BTrace fat agent JAR with embedded extensions.
 *
 * <p>This goal resolves BTrace extension artifacts from Maven repositories, stages their API and
 * implementation classes appropriately, and packages everything into a single deployable agent JAR.
 */
@Mojo(
    name = "fat-agent",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME)
public class FatAgentMojo extends AbstractMojo {

  private static final String BTRACE_GROUP_ID = "io.btrace";
  private static final String BTRACE_AGENT_ARTIFACT_ID = "btrace-agent";
  private static final String BTRACE_BOOT_ARTIFACT_ID = "btrace-boot";
  private static final String EXTENSION_METADATA_PATH = "META-INF/btrace-extensions/";
  private static final String CLASSDATA_SUFFIX = ".classdata";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Component private RepositorySystem repoSystem;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
  private RepositorySystemSession repoSession;

  @Parameter(
      defaultValue = "${project.remoteProjectRepositories}",
      readonly = true,
      required = true)
  private List<RemoteRepository> remoteRepositories;

  /** BTrace version to use. Defaults to the plugin version. */
  @Parameter(property = "btrace.version", defaultValue = "${plugin.version}")
  private String btraceVersion;

  /** List of extension coordinates to embed (groupId:artifactId:version). */
  @Parameter(property = "btrace.extensions")
  private List<String> extensions;

  /** Output file name (without .jar extension). */
  @Parameter(property = "btrace.outputName", defaultValue = "btrace-agent-fat")
  private String outputName;

  /** Output directory. */
  @Parameter(property = "btrace.outputDirectory", defaultValue = "${project.build.directory}")
  private File outputDirectory;

  /** Skip execution. */
  @Parameter(property = "btrace.skip", defaultValue = "false")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping BTrace fat agent build");
      return;
    }

    getLog().info("Building BTrace fat agent JAR...");

    try {
      // Create staging directory
      Path stagingDir = Files.createTempDirectory("btrace-fat-agent-staging");

      // Resolve and extract base agent JAR
      File agentJar = resolveArtifact(BTRACE_GROUP_ID, BTRACE_AGENT_ARTIFACT_ID, btraceVersion);
      getLog().info("Resolved btrace-agent: " + agentJar);

      // Resolve and extract boot JAR
      File bootJar = resolveArtifact(BTRACE_GROUP_ID, BTRACE_BOOT_ARTIFACT_ID, btraceVersion);
      getLog().info("Resolved btrace-boot: " + bootJar);

      // Extract agent JAR as base
      extractJar(agentJar, stagingDir);

      // Extract boot JAR (overlay)
      extractJar(bootJar, stagingDir);

      // Process extensions
      List<String> embeddedExtensionIds = new ArrayList<>();
      if (extensions != null && !extensions.isEmpty()) {
        for (String extCoords : extensions) {
          String extId = processExtension(extCoords, stagingDir);
          if (extId != null) {
            embeddedExtensionIds.add(extId);
          }
        }
      }

      // Update manifest with embedded extensions list
      updateManifest(stagingDir, embeddedExtensionIds);

      // Create output JAR
      File outputJar = new File(outputDirectory, outputName + ".jar");
      createJar(stagingDir, outputJar);

      getLog().info("Created fat agent JAR: " + outputJar);
      getLog()
          .info(
              "Embedded " + embeddedExtensionIds.size() + " extension(s): " + embeddedExtensionIds);

      // Attach artifact to project
      project.getArtifact().setFile(outputJar);

      // Cleanup
      deleteDirectory(stagingDir.toFile());

    } catch (IOException e) {
      throw new MojoExecutionException("Failed to build fat agent JAR", e);
    }
  }

  private File resolveArtifact(String groupId, String artifactId, String version)
      throws MojoExecutionException {
    DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(artifact);
    request.setRepositories(remoteRepositories);

    try {
      ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
      return result.getArtifact().getFile();
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException("Failed to resolve artifact: " + artifact, e);
    }
  }

  private void extractJar(File jarFile, Path targetDir) throws IOException {
    Path normalizedTarget = targetDir.toAbsolutePath().normalize();
    try (JarFile jar = new JarFile(jarFile)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        Path targetPath = resolveSecurely(normalizedTarget, entry.getName());

        if (entry.isDirectory()) {
          Files.createDirectories(targetPath);
        } else {
          Files.createDirectories(targetPath.getParent());
          try (InputStream is = jar.getInputStream(entry)) {
            Files.copy(is, targetPath);
          }
        }
      }
    }
  }

  /**
   * Resolves a path securely, preventing directory traversal attacks (Zip Slip).
   *
   * @param baseDir the base directory (must be absolute and normalized)
   * @param entryName the entry name to resolve
   * @return the resolved path
   * @throws IOException if the resolved path escapes the base directory
   */
  // Package-private for testing
  Path resolveSecurely(Path baseDir, String entryName) throws IOException {
    Path resolved = baseDir.resolve(entryName).normalize();
    if (!resolved.startsWith(baseDir)) {
      throw new IOException("Entry would escape target directory: " + entryName);
    }
    return resolved;
  }

  /**
   * Process an extension artifact.
   *
   * <p>Extensions are expected to have: - API JAR: contains classes for bootstrap classloader -
   * Impl JAR: contains implementation classes (renamed to .classdata) - Metadata:
   * extension.properties with id, services, etc.
   *
   * @param coordinates Maven coordinates (groupId:artifactId:version)
   * @param stagingDir staging directory
   * @return extension ID, or null if processing failed
   */
  private String processExtension(String coordinates, Path stagingDir)
      throws MojoExecutionException, IOException {
    String[] parts = coordinates.split(":");
    if (parts.length < 3) {
      getLog()
          .warn(
              "Invalid extension coordinates: "
                  + coordinates
                  + " (expected groupId:artifactId:version)");
      return null;
    }

    String groupId = parts[0];
    String artifactId = parts[1];
    String version = parts[2];

    getLog().info("Processing extension: " + coordinates);

    // Resolve API JAR (classifier: api)
    File apiJar;
    try {
      apiJar = resolveArtifactWithClassifier(groupId, artifactId, version, "api");
    } catch (MojoExecutionException e) {
      // Try without classifier (some extensions might be single-JAR)
      getLog().debug("No API JAR found, trying main artifact");
      apiJar = resolveArtifact(groupId, artifactId, version);
    }

    // Resolve Impl JAR (classifier: impl)
    File implJar;
    try {
      implJar = resolveArtifactWithClassifier(groupId, artifactId, version, "impl");
    } catch (MojoExecutionException e) {
      getLog().debug("No Impl JAR found for " + coordinates);
      implJar = null;
    }

    // Extract extension ID from API JAR manifest
    String extensionId = extractExtensionId(apiJar);
    if (extensionId == null) {
      extensionId = artifactId; // fallback to artifact ID
    }

    // Validate extension ID to prevent path traversal attacks
    if (!isValidExtensionId(extensionId)) {
      throw new IOException("Invalid extension ID (potential path traversal): " + extensionId);
    }

    // Stage API classes (as .class files for bootstrap)
    stageApiClasses(apiJar, stagingDir);

    // Stage Impl classes (as .classdata files for runtime loading)
    if (implJar != null) {
      stageImplClasses(implJar, stagingDir, extensionId);
    }

    // Stage extension metadata
    stageExtensionMetadata(apiJar, stagingDir, extensionId);

    return extensionId;
  }

  private File resolveArtifactWithClassifier(
      String groupId, String artifactId, String version, String classifier)
      throws MojoExecutionException {
    DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, classifier, "jar", version);
    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(artifact);
    request.setRepositories(remoteRepositories);

    try {
      ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
      return result.getArtifact().getFile();
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException("Failed to resolve artifact: " + artifact, e);
    }
  }

  private String extractExtensionId(File jarFile) throws IOException {
    try (JarFile jar = new JarFile(jarFile)) {
      Manifest manifest = jar.getManifest();
      if (manifest != null) {
        return manifest.getMainAttributes().getValue("BTrace-Extension-Id");
      }
    }
    return null;
  }

  private void stageApiClasses(File apiJar, Path stagingDir) throws IOException {
    Path normalizedStaging = stagingDir.toAbsolutePath().normalize();
    try (JarFile jar = new JarFile(apiJar)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        // Only copy .class files (not META-INF or other resources)
        if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
          Path targetPath = resolveSecurely(normalizedStaging, name);
          Files.createDirectories(targetPath.getParent());
          try (InputStream is = jar.getInputStream(entry)) {
            Files.copy(is, targetPath);
          }
        }
      }
    }
  }

  private void stageImplClasses(File implJar, Path stagingDir, String extensionId)
      throws IOException {
    Path implDir = stagingDir.resolve(EXTENSION_METADATA_PATH).resolve(extensionId).resolve("impl");
    Files.createDirectories(implDir);
    Path normalizedImplDir = implDir.toAbsolutePath().normalize();

    try (JarFile jar = new JarFile(implJar)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        // Rename .class to .classdata for runtime loading
        if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
          String classdataName = name.substring(0, name.length() - 6) + CLASSDATA_SUFFIX;
          Path targetPath = resolveSecurely(normalizedImplDir, classdataName);
          Files.createDirectories(targetPath.getParent());
          try (InputStream is = jar.getInputStream(entry)) {
            Files.copy(is, targetPath);
          }
        }
      }
    }
  }

  private void stageExtensionMetadata(File apiJar, Path stagingDir, String extensionId)
      throws IOException {
    Path metadataDir = stagingDir.resolve(EXTENSION_METADATA_PATH).resolve(extensionId);
    Files.createDirectories(metadataDir);

    // Extract extension.properties from API JAR if present
    try (JarFile jar = new JarFile(apiJar)) {
      JarEntry propsEntry = jar.getJarEntry("META-INF/btrace/extension.properties");
      if (propsEntry != null) {
        try (InputStream is = jar.getInputStream(propsEntry)) {
          Files.copy(is, metadataDir.resolve("extension.properties"));
        }
      } else {
        // Create minimal extension.properties from manifest
        Properties props = new Properties();
        Manifest manifest = jar.getManifest();
        if (manifest != null) {
          Attributes attrs = manifest.getMainAttributes();
          props.setProperty("id", extensionId);
          String name = attrs.getValue("BTrace-Extension-Name");
          if (name != null) props.setProperty("name", name);
          String services = attrs.getValue("BTrace-Extension-Services");
          if (services != null) props.setProperty("services", services);
          String permissions = attrs.getValue("BTrace-Extension-Permissions");
          if (permissions != null) props.setProperty("permissions", permissions);
        }
        try (OutputStream os = Files.newOutputStream(metadataDir.resolve("extension.properties"))) {
          props.store(os, "BTrace Extension Metadata");
        }
      }
    }
  }

  private void updateManifest(Path stagingDir, List<String> embeddedExtensionIds)
      throws IOException {
    Path manifestPath = stagingDir.resolve("META-INF/MANIFEST.MF");
    Manifest manifest;

    if (Files.exists(manifestPath)) {
      try (InputStream is = Files.newInputStream(manifestPath)) {
        manifest = new Manifest(is);
      }
    } else {
      manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    }

    // Add embedded extensions attribute
    if (!embeddedExtensionIds.isEmpty()) {
      manifest
          .getMainAttributes()
          .putValue("BTrace-Embedded-Extensions", String.join(",", embeddedExtensionIds));
    }

    Files.createDirectories(manifestPath.getParent());
    try (OutputStream os = Files.newOutputStream(manifestPath)) {
      manifest.write(os);
    }
  }

  private void createJar(Path sourceDir, File outputJar) throws IOException {
    Files.createDirectories(outputJar.getParentFile().toPath());

    // Read manifest
    Path manifestPath = sourceDir.resolve("META-INF/MANIFEST.MF");
    Manifest manifest;
    if (Files.exists(manifestPath)) {
      try (InputStream is = Files.newInputStream(manifestPath)) {
        manifest = new Manifest(is);
      }
    } else {
      manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    }

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add("META-INF/MANIFEST.MF"); // Already added via constructor

      Files.walk(sourceDir)
          .forEach(
              path -> {
                if (Files.isRegularFile(path)) {
                  String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                  if (!addedEntries.contains(entryName)) {
                    try {
                      jos.putNextEntry(new ZipEntry(entryName));
                      Files.copy(path, jos);
                      jos.closeEntry();
                      addedEntries.add(entryName);
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to add entry: " + entryName, e);
                    }
                  }
                }
              });
    }
  }

  private void deleteDirectory(File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          file.delete();
        }
      }
    }
    dir.delete();
  }

  /**
   * Validates that an extension ID is safe and cannot be used for path traversal.
   *
   * <p>Valid extension IDs must:
   *
   * <ul>
   *   <li>Not be null or empty
   *   <li>Not contain path separators (/ or \)
   *   <li>Not contain parent directory references (..)
   *   <li>Only contain safe characters: alphanumeric, hyphen, underscore, dot
   * </ul>
   *
   * @param extensionId the extension ID to validate
   * @return true if the ID is safe, false otherwise
   */
  // Package-private for testing
  static boolean isValidExtensionId(String extensionId) {
    if (extensionId == null || extensionId.isEmpty()) {
      return false;
    }
    // Reject path separators
    if (extensionId.contains("/") || extensionId.contains("\\")) {
      return false;
    }
    // Reject parent directory references
    if (extensionId.contains("..")) {
      return false;
    }
    // Only allow safe characters: alphanumeric, hyphen, underscore, dot
    // This pattern matches valid Maven artifact IDs
    return extensionId.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  }
}
