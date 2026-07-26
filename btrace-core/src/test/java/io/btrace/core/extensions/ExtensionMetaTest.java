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
package io.btrace.core.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.jar.Attributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Extension inspection must report what the loader will enforce.
 *
 * <p>Metadata used to be read only from a package-level {@code @ExtensionDescriptor}, which is
 * optional: extensions without one were reported under their implementation class's simple name,
 * with no version and - more importantly - an empty permission set, while extensions with one
 * reported a hand-written version the build never propagated. The manifest is what the Gradle
 * plugin emits and what the runtime parses, so it is what inspection reports.
 */
class ExtensionMetaTest {

  private static final String ID = "BTrace-Extension-Id";
  private static final String NAME = "BTrace-Extension-Name";
  private static final String VERSION = "BTrace-Extension-Version";
  private static final String DESCRIPTION = "BTrace-Extension-Description";
  private static final String API_VERSION = "BTrace-API-Version";
  private static final String PERMISSIONS = "BTrace-Extension-Permissions";

  /** An extension carrying no package-level descriptor, like most of the shipped ones. */
  public static final class UndescribedExtension extends Extension {}

  @Test
  @DisplayName("manifest supplies identity, version and permissions")
  void manifestSuppliesMetadata() {
    Attributes attributes = new Attributes();
    attributes.putValue(ID, "btrace-metrics");
    attributes.putValue(NAME, "BTrace Metrics");
    attributes.putValue(VERSION, "3.0.0");
    attributes.putValue(DESCRIPTION, "High-performance metrics");
    attributes.putValue(API_VERSION, "3.0+");
    attributes.putValue(PERMISSIONS, "THREADS,NETWORK");

    ExtensionMeta meta = ExtensionMeta.from(UndescribedExtension.class, attributes);

    assertEquals("BTrace Metrics", meta.getName());
    assertEquals("3.0.0", meta.getVersion());
    assertEquals("High-performance metrics", meta.getDescription());
    assertEquals("3.0+", meta.getMinBTraceVersion());
    assertTrue(meta.getRequiredPermissions().has(Permission.THREADS));
    assertTrue(meta.getRequiredPermissions().has(Permission.NETWORK));
  }

  @Test
  @DisplayName("the extension id names the extension when no display name is set")
  void idIsUsedWhenNameAbsent() {
    Attributes attributes = new Attributes();
    attributes.putValue(ID, "btrace-contracts");
    attributes.putValue(VERSION, "3.0.0");

    assertEquals(
        "btrace-contracts", ExtensionMeta.from(UndescribedExtension.class, attributes).getName());
  }

  @Test
  @DisplayName("permissions are reported for extensions with no package descriptor")
  void permissionsReportedWithoutPackageDescriptor() {
    Attributes attributes = new Attributes();
    attributes.putValue(ID, "btrace-statsd");
    attributes.putValue(VERSION, "3.0.0");
    attributes.putValue(PERMISSIONS, "NETWORK");

    ExtensionMeta meta = ExtensionMeta.from(UndescribedExtension.class, attributes);

    assertTrue(
        meta.getRequiredPermissions().has(Permission.NETWORK),
        "an extension without @ExtensionDescriptor must still report its manifest permissions");
  }

  @Test
  @DisplayName("without a manifest the class name identifies the extension")
  void fallsBackToClassNameWithoutManifest() {
    ExtensionMeta meta = ExtensionMeta.from(UndescribedExtension.class, null);

    assertEquals("UndescribedExtension", meta.getName());
    assertEquals("", meta.getVersion());
    assertTrue(meta.getRequiredPermissions().isEmpty(), "expected no permissions");
  }

  @Test
  @DisplayName("the single-argument overload behaves as no manifest")
  void singleArgumentOverloadMatchesNullManifest() {
    ExtensionMeta withoutManifest = ExtensionMeta.from(UndescribedExtension.class);
    ExtensionMeta nullManifest = ExtensionMeta.from(UndescribedExtension.class, null);

    assertEquals(nullManifest.getName(), withoutManifest.getName());
    assertEquals(nullManifest.getVersion(), withoutManifest.getVersion());
  }

  @Test
  @DisplayName("blank manifest values do not mask the fallback")
  void blankManifestValuesAreIgnored() {
    Attributes attributes = new Attributes();
    attributes.putValue(NAME, "   ");
    attributes.putValue(VERSION, "");

    ExtensionMeta meta = ExtensionMeta.from(UndescribedExtension.class, attributes);

    assertEquals("UndescribedExtension", meta.getName());
    assertEquals("", meta.getVersion());
  }
}
