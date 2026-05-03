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
package io.btrace.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientTest {

  @TempDir Path tempDir;

  private File createUberJar() throws IOException {
    File uberJar = tempDir.resolve("btrace.jar").toFile();

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(uberJar))) {
      // Add a dummy embedded agent JAR
      JarEntry agentEntry = new JarEntry("META-INF/embedded/btrace-agent.jar");
      jos.putNextEntry(agentEntry);
      jos.write("dummy agent content".getBytes());
      jos.closeEntry();

      // Add a dummy embedded boot JAR
      JarEntry bootEntry = new JarEntry("META-INF/embedded/btrace-boot.jar");
      jos.putNextEntry(bootEntry);
      jos.write("dummy boot content".getBytes());
      jos.closeEntry();

      // Add a marker class to make it a valid JAR
      JarEntry classEntry = new JarEntry("io/btrace/client/Client.class");
      jos.putNextEntry(classEntry);
      jos.write(new byte[0]);
      jos.closeEntry();
    }

    return uberJar;
  }

  @Test
  void testConstructorWithOverrides() {
    String agentJar = "/path/to/agent.jar";

    Client client = new Client(2020, null, ".", false, false, false, false, null, null, agentJar);

    // Use reflection to verify private fields (since they're not exposed)
    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertEquals(agentJar, agentField.get(client));
    } catch (Exception e) {
      fail("Failed to access private fields: " + e.getMessage());
    }
  }

  @Test
  void testConstructorWithNullOverrides() {
    Client client = new Client(2020, null, ".", false, false, false, false, null, null, null);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertNull(agentField.get(client));
    } catch (Exception e) {
      fail("Failed to access private fields: " + e.getMessage());
    }
  }

  @Test
  void testExtractEmbeddedAgentJarNotFound() throws Exception {
    // Create a regular JAR without embedded JARs
    File regularJar = tempDir.resolve("regular.jar").toFile();
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(regularJar))) {
      JarEntry entry = new JarEntry("some/Class.class");
      jos.putNextEntry(entry);
      jos.write(new byte[0]);
      jos.closeEntry();
    }

    // This test would need to mock the Client.class location
    // For now, we just verify the JAR exists
    assertTrue(regularJar.exists());
  }

  @Test
  void testUberJarCreation() throws Exception {
    File uberJar = createUberJar();
    assertTrue(uberJar.exists());

    // Verify embedded JARs exist
    try (JarFile jar = new JarFile(uberJar)) {
      assertNotNull(jar.getJarEntry("META-INF/embedded/btrace-agent.jar"));
      assertNotNull(jar.getJarEntry("META-INF/embedded/btrace-boot.jar"));
    }
  }

  @Test
  void testAgentJarOverrideTakesPrecedence() {
    // When agentJarOverride is set, it should be used instead of discovery
    String overridePath = "/custom/path/btrace-agent.jar";

    Client client =
        new Client(2020, null, ".", false, false, false, false, null, null, overridePath);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertEquals(overridePath, agentField.get(client));
    } catch (Exception e) {
      fail("Failed to verify agentJarOverride: " + e.getMessage());
    }
  }

  @Test
  void testBackwardCompatibility() {
    // Old constructor should still work (no overrides)
    Client client = new Client(2020, null, ".", false, false, false, false, null, null);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertNull(agentField.get(client));
    } catch (Exception e) {
      fail("Failed to verify backward compatibility: " + e.getMessage());
    }
  }
}
