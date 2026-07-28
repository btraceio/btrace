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

import io.btrace.extension.ExtensionDescriptorDTO;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers which names an extension reports as its services. */
class ExtensionMetadataServicesTest {

  @TempDir Path tempDir;

  /**
   * Writes an extension API jar.
   *
   * @param servicesAttr value for the manifest's service list, or null to omit it
   * @param providerFor service interface to write a provider file for, or null for none
   * @param providerContent implementing class named inside that provider file
   */
  private Path apiJar(String servicesAttr, String providerFor, String providerContent)
      throws IOException {
    Path jar =
        tempDir.resolve(
            "ext-" + Math.abs(("" + servicesAttr + providerFor).hashCode()) + "-api.jar");
    Manifest mf = new Manifest();
    mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mf.getMainAttributes().putValue("BTrace-Extension-Id", "demo-ext");
    mf.getMainAttributes().putValue("BTrace-Extension-Version", "1.0.0");
    if (servicesAttr != null) {
      mf.getMainAttributes().putValue("BTrace-Extension-Services", servicesAttr);
    }
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()), mf)) {
      if (providerFor != null) {
        out.putNextEntry(new JarEntry("META-INF/services/" + providerFor));
        out.write(providerContent.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    return jar;
  }

  @Test
  void aDeclaredProviderDoesNotBecomeAService() throws IOException {
    // An extension whose implementation is not at <service>Impl must ship a provider file. The
    // file names the implementing class, which is not a service the extension offers.
    Path jar =
        apiJar("com.example.OrderApi", "com.example.OrderApi", "com.example.impl.OrderApiImpl\n");

    ExtensionDescriptorDTO dto = ExtensionMetadata.parse(jar, tempDir, null);

    assertEquals(Collections.singletonList("com.example.OrderApi"), dto.getServices());
    assertFalse(
        dto.getServices().contains("com.example.impl.OrderApiImpl"),
        "an implementing class must not be reported as a service: " + dto.getServices());
  }

  @Test
  void providerFileNamesAreUsedWhenTheManifestDeclaresNothing() throws IOException {
    // Fallback for extensions predating the manifest attribute: the file name is the interface.
    Path jar = apiJar(null, "com.example.OrderApi", "com.example.impl.OrderApiImpl\n");

    ExtensionDescriptorDTO dto = ExtensionMetadata.parse(jar, tempDir, null);

    assertEquals(Collections.singletonList("com.example.OrderApi"), dto.getServices());
  }
}
