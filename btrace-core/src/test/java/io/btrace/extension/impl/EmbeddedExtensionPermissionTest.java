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
package io.btrace.extension.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.extensions.Permission;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.PermissionPolicy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddedExtensionPermissionTest {
  private static final String EXTENSION_ID = "embedded-privileged-test";
  private static final String SERVICE = "test.ext.Service";

  @TempDir Path tempDir;

  @BeforeEach
  void resetPolicy() {
    PermissionPolicy policy = PermissionPolicy.get();
    policy.setAllowExtensionsCsv("");
    policy.setDenyExtensionsCsv("");
    policy.setAllowPrivileged(false);
  }

  @Test
  void embeddedAndFilesystemRepositoriesPreserveTheSamePermissions() throws Exception {
    ExtensionDescriptorDTO embedded = scanEmbeddedExtension();
    ExtensionDescriptorDTO filesystem = scanFilesystemExtension();

    assertTrue(embedded.isEmbedded());
    assertFalse(filesystem.isEmbedded());
    assertEquals(filesystem.getRequiredPermissions(), embedded.getRequiredPermissions());
    assertTrue(embedded.getRequiredPermissions().has(Permission.NETWORK));

    TestLoader embeddedLoader = new TestLoader(embedded);
    TestLoader filesystemLoader = new TestLoader(filesystem);
    assertNull(new ExtensionBridgeImpl(embeddedLoader).getExtensionClass(SERVICE));
    assertNull(new ExtensionBridgeImpl(filesystemLoader).getExtensionClass(SERVICE));
    assertFalse(embeddedLoader.loaded);
    assertFalse(filesystemLoader.loaded);
  }

  @Test
  void embeddedExtensionPreservesRequiredExtensions() throws Exception {
    ExtensionDescriptorDTO embedded = scanEmbeddedExtension();

    assertEquals(
        Collections.singletonList("embedded-prerequisite"), embedded.getRequiredExtensions());
  }

  @Test
  void bridgeDeniesPrivilegedEmbeddedExtensionByDefault() throws Exception {
    ExtensionDescriptorDTO embedded = scanEmbeddedExtension();
    TestLoader loader = new TestLoader(embedded);

    Class<?> implementation = new ExtensionBridgeImpl(loader).getExtensionClass(SERVICE);

    assertNull(implementation);
    assertFalse(loader.loaded, "denied embedded implementation must not be loaded");
  }

  @Test
  void explicitPolicyAllowsPrivilegedEmbeddedExtension() throws Exception {
    ExtensionDescriptorDTO embedded = scanEmbeddedExtension();
    PermissionPolicy.get().setAllowExtensionsCsv(EXTENSION_ID);
    TestLoader loader = new TestLoader(embedded);

    Class<?> implementation = new ExtensionBridgeImpl(loader).getExtensionClass(SERVICE);

    assertTrue(loader.loaded);
    assertEquals("test.ext.SpiImpl", implementation.getName());
  }

  private ExtensionDescriptorDTO scanEmbeddedExtension() throws Exception {
    Path jar = tempDir.resolve("embedded-extension.jar");
    Manifest manifest = manifest();
    manifest.getMainAttributes().putValue("BTrace-Embedded-Extensions", EXTENSION_ID);
    String properties =
        "id="
            + EXTENSION_ID
            + "\nversion=1.0.0\nname=Embedded privileged test\nservices="
            + SERVICE
            + "\nrequires.extensions=embedded-prerequisite\npermissions=NETWORK\n";
    writeJar(
        jar,
        manifest,
        "META-INF/btrace-extensions/" + EXTENSION_ID + "/extension.properties",
        properties);

    try (URLClassLoader resourceLoader =
        new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader())) {
      List<ExtensionDescriptorDTO> descriptors =
          new EmbeddedExtensionRepository(resourceLoader).scan();
      assertEquals(1, descriptors.size());
      return descriptors.get(0);
    }
  }

  private ExtensionDescriptorDTO scanFilesystemExtension() throws IOException {
    Path extensionDir = tempDir.resolve("filesystem").resolve(EXTENSION_ID);
    Files.createDirectories(extensionDir);
    Manifest manifest = manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.putValue("BTrace-Extension-Id", EXTENSION_ID);
    attributes.putValue("BTrace-Extension-Version", "1.0.0");
    attributes.putValue("BTrace-Extension-Name", "Filesystem privileged test");
    attributes.putValue("BTrace-Extension-Services", SERVICE);
    attributes.putValue("BTrace-Extension-Permissions", "NETWORK");
    writeJar(extensionDir.resolve("permission-test-api.jar"), manifest, null, null);

    List<ExtensionDescriptorDTO> descriptors =
        new FileSystemExtensionRepository(tempDir.resolve("filesystem"), 0).scan();
    assertEquals(1, descriptors.size());
    return descriptors.get(0);
  }

  private static Manifest manifest() {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    return manifest;
  }

  private static void writeJar(Path jar, Manifest manifest, String entryName, String contents)
      throws IOException {
    try (OutputStream output = Files.newOutputStream(jar);
        JarOutputStream jarOutput = new JarOutputStream(output, manifest)) {
      if (entryName != null) {
        jarOutput.putNextEntry(new JarEntry(entryName));
        jarOutput.write(contents.getBytes(StandardCharsets.ISO_8859_1));
        jarOutput.closeEntry();
      }
    }
  }

  private static final class TestLoader extends ExtensionLoader {
    private final ExtensionDescriptorDTO descriptor;
    private boolean loaded;

    private TestLoader(ExtensionDescriptorDTO descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ExtensionDescriptorDTO findExtensionForService(String serviceClassName) {
      return descriptor;
    }

    @Override
    public List<ExtensionDescriptorDTO> discoverExtensions() {
      return Collections.singletonList(descriptor);
    }

    @Override
    public Collection<ExtensionDescriptorDTO> getAvailableExtensions() {
      return Collections.singletonList(descriptor);
    }

    @Override
    public boolean ensureApiOnBootstrap(ExtensionDescriptorDTO ignored) {
      return true;
    }

    @Override
    public boolean load(ExtensionDescriptorDTO descriptor) {
      loaded = true;
      descriptor.setClassLoader(EmbeddedExtensionPermissionTest.class.getClassLoader());
      return true;
    }
  }
}
