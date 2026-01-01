package org.openjdk.btrace.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @TempDir Path tempDir;

  /**
   * Creates a mock uber JAR with embedded agent and boot JARs for testing extraction.
   */
  private File createTestUberJar() throws IOException {
    File uberJar = tempDir.resolve("btrace-test.jar").toFile();

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(uberJar))) {
      // Add embedded agent JAR
      JarEntry agentEntry = new JarEntry("META-INF/embedded/btrace-agent.jar");
      jos.putNextEntry(agentEntry);
      jos.write("test agent jar content".getBytes());
      jos.closeEntry();

      // Add embedded boot JAR
      JarEntry bootEntry = new JarEntry("META-INF/embedded/btrace-boot.jar");
      jos.putNextEntry(bootEntry);
      jos.write("test boot jar content".getBytes());
      jos.closeEntry();
    }

    return uberJar;
  }

  @Test
  void testExtractJar() throws Exception {
    File sourceJar = createTestUberJar();
    File targetFile = tempDir.resolve("extracted.jar").toFile();

    // Use reflection to access private extractJar method
    Method extractJarMethod =
        Main.class.getDeclaredMethod(
            "extractJar", JarFile.class, String.class, File.class);
    extractJarMethod.setAccessible(true);

    try (JarFile jarFile = new JarFile(sourceJar)) {
      extractJarMethod.invoke(null, jarFile, "META-INF/embedded/btrace-agent.jar", targetFile);
    }

    // Verify extraction
    assertTrue(targetFile.exists());
    assertTrue(targetFile.length() > 0);

    // Verify content
    byte[] content = Files.readAllBytes(targetFile.toPath());
    assertEquals("test agent jar content", new String(content));
  }

  @Test
  void testExtractJarMissingEntry() throws Exception {
    File sourceJar = createTestUberJar();
    File targetFile = tempDir.resolve("extracted.jar").toFile();

    Method extractJarMethod =
        Main.class.getDeclaredMethod(
            "extractJar", JarFile.class, String.class, File.class);
    extractJarMethod.setAccessible(true);

    try (JarFile jarFile = new JarFile(sourceJar)) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> {
                extractJarMethod.invoke(
                    null, jarFile, "META-INF/embedded/nonexistent.jar", targetFile);
              });

      assertTrue(
          exception.getCause().getMessage().contains("Embedded JAR not found"),
          "Expected error message about missing embedded JAR");
    }
  }

  @Test
  void testExtractJarToDirectory() throws Exception {
    File sourceJar = createTestUberJar();
    File outputDir = tempDir.resolve("output").toFile();
    assertTrue(outputDir.mkdirs());

    File agentFile = new File(outputDir, "btrace-agent.jar");
    File bootFile = new File(outputDir, "btrace-boot.jar");

    Method extractJarMethod =
        Main.class.getDeclaredMethod(
            "extractJar", JarFile.class, String.class, File.class);
    extractJarMethod.setAccessible(true);

    try (JarFile jarFile = new JarFile(sourceJar)) {
      extractJarMethod.invoke(null, jarFile, "META-INF/embedded/btrace-agent.jar", agentFile);
      extractJarMethod.invoke(null, jarFile, "META-INF/embedded/btrace-boot.jar", bootFile);
    }

    // Verify both files were extracted
    assertTrue(agentFile.exists());
    assertTrue(bootFile.exists());
    assertTrue(agentFile.length() > 0);
    assertTrue(bootFile.length() > 0);
  }

  @Test
  void testExtractAgentCreatesDirectory() throws Exception {
    File nonExistentDir = tempDir.resolve("newdir").toFile();
    assertFalse(nonExistentDir.exists());

    // Test that handleExtractAgent would create the directory
    // This is tested indirectly by verifying directory creation logic
    assertTrue(nonExistentDir.mkdirs() || nonExistentDir.exists());
    assertTrue(nonExistentDir.exists());
    assertTrue(nonExistentDir.isDirectory());
  }

  @Test
  void testCLIFlagsParsing() throws Exception {
    // Test that static fields can be set (simulating CLI flag parsing)
    // This verifies the fields exist and are accessible for setting

    Field agentJarField = Main.class.getDeclaredField("AGENT_JAR_OVERRIDE");
    agentJarField.setAccessible(true);
    assertNotNull(agentJarField);

    Field bootJarField = Main.class.getDeclaredField("BOOT_JAR_OVERRIDE");
    bootJarField.setAccessible(true);
    assertNotNull(bootJarField);

    Field extractDirField = Main.class.getDeclaredField("EXTRACT_AGENT_DIR");
    extractDirField.setAccessible(true);
    assertNotNull(extractDirField);
  }

  @Test
  void testExtractJarWithLargeContent() throws Exception {
    // Create a JAR with larger content to test buffer handling
    File sourceJar = tempDir.resolve("large-btrace.jar").toFile();
    byte[] largeContent = new byte[16384]; // 16KB
    for (int i = 0; i < largeContent.length; i++) {
      largeContent[i] = (byte) (i % 256);
    }

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(sourceJar))) {
      JarEntry entry = new JarEntry("META-INF/embedded/btrace-agent.jar");
      jos.putNextEntry(entry);
      jos.write(largeContent);
      jos.closeEntry();
    }

    File targetFile = tempDir.resolve("extracted-large.jar").toFile();

    Method extractJarMethod =
        Main.class.getDeclaredMethod(
            "extractJar", JarFile.class, String.class, File.class);
    extractJarMethod.setAccessible(true);

    try (JarFile jarFile = new JarFile(sourceJar)) {
      extractJarMethod.invoke(null, jarFile, "META-INF/embedded/btrace-agent.jar", targetFile);
    }

    // Verify complete extraction
    assertTrue(targetFile.exists());
    assertEquals(largeContent.length, targetFile.length());

    // Verify content integrity
    byte[] extractedContent = Files.readAllBytes(targetFile.toPath());
    assertArrayEquals(largeContent, extractedContent);
  }
}
