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
package io.btrace.extension;

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;
import io.btrace.extension.impl.ExtensionBridgeImpl;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionBridgeImplPolicyTest {

  private static final String EXT_ID_BASE = "test-ext";
  private static final String SERVICE_IFACE = "test.ext.Service"; // with ServiceLoader provider
  private static final String SERVICE_IFACE2 = "test.ext2.Service2"; // conventional Impl only

  private static class DummyLoader extends ExtensionLoader {
    final ExtensionDescriptorDTO desc;
    boolean apiOnBoot;
    boolean loaded;

    DummyLoader(ExtensionDescriptorDTO desc) {
      this.desc = desc;
    }

    @Override
    public ExtensionDescriptorDTO findExtensionForService(String serviceClassName) {
      return desc;
    }

    @Override
    public java.util.List<ExtensionDescriptorDTO> discoverExtensions() {
      return Collections.singletonList(desc);
    }

    @Override
    public java.util.Collection<ExtensionDescriptorDTO> getAvailableExtensions() {
      return Collections.singletonList(desc);
    }

    @Override
    public boolean ensureApiOnBootstrap(ExtensionDescriptorDTO descriptor) {
      apiOnBoot = true;
      return true;
    }

    @Override
    public boolean load(ExtensionDescriptorDTO descriptor) {
      loaded = true;
      return true;
    }
  }

  private ExtensionDescriptorDTO.Builder baseBuilder(String serviceFqcn, String extId) {
    return new ExtensionDescriptorDTO.Builder()
        .id(extId)
        .version("1.0.0")
        .name("Test")
        .description("Test")
        .jarPath(Paths.get("/tmp/test.jar"))
        .services(Collections.singletonList(serviceFqcn))
        .requiredExtensions(Collections.emptyList())
        .repository(null)
        .requiredPermissions(PermissionSet.empty());
  }

  @BeforeEach
  void resetPolicy() {
    PermissionPolicy p = PermissionPolicy.get();
    p.setAllowExtensionsCsv("");
    p.setDenyExtensionsCsv("");
    p.setAllowPrivileged(false);
  }

  @Test
  void denyListTriggersFallbackAndRegistersFailure() throws Exception {
    String extId = EXT_ID_BASE + "-deny";
    PermissionPolicy.get().setDenyExtensionsCsv(extId);
    ExtensionDescriptorDTO dto = baseBuilder(SERVICE_IFACE, extId).build();
    DummyLoader dl = new DummyLoader(dto);
    ExtensionBridgeImpl bridge = new ExtensionBridgeImpl(dl);

    Class<?> clz = bridge.getExtensionClass(SERVICE_IFACE);
    assertNull(
        clz, "Expected null when denied; serviceClass=" + SERVICE_IFACE + ", extId=" + extId);
    assertTrue(dl.apiOnBoot, "API should be ensured on bootstrap; extId=" + extId);
    assertFalse(dl.loaded, "Implementation must not be loaded when denied; extId=" + extId);
    String reason = ExtensionRegistry.getFailedExtensions().get(extId);
    assertEquals(
        "Blocked by policy (denyExtensions)",
        reason,
        "Unexpected failure reason for extId=" + extId + ": " + reason);
  }

  @Test
  void privilegedWithoutAllowTriggersFallback() throws Exception {
    String extId = EXT_ID_BASE + "-priv";
    PermissionSet perms = PermissionSet.of(Permission.THREADS);
    ExtensionDescriptorDTO dto =
        baseBuilder(SERVICE_IFACE, extId).requiredPermissions(perms).build();
    DummyLoader dl = new DummyLoader(dto);
    ExtensionBridgeImpl bridge = new ExtensionBridgeImpl(dl);

    Class<?> clz = bridge.getExtensionClass(SERVICE_IFACE);
    assertNull(clz, "Expected null when privileged extension is not allowed; extId=" + extId);
    assertTrue(
        dl.apiOnBoot, "API should be ensured on bootstrap for privileged fallback; extId=" + extId);
    assertFalse(
        dl.loaded,
        "Privileged implementation should not be loaded when not allowed; extId=" + extId);
    String reason = ExtensionRegistry.getFailedExtensions().get(extId);
    assertNotNull(reason, "Expected a failure reason to be recorded; extId=" + extId);
    assertTrue(
        reason.startsWith("Blocked privileged extension."),
        "Unexpected failure reason for extId=" + extId + ": " + reason);
  }

  @Test
  void allowedLoadsAndFindsConventionalImpl() throws Exception {
    // Allow privileged to ensure no policy block
    PermissionPolicy.get().setAllowPrivileged(true);
    String extId = EXT_ID_BASE + "-conv";
    ExtensionDescriptorDTO dto = baseBuilder(SERVICE_IFACE2, extId).build();
    DummyLoader dl = new DummyLoader(dto);
    // Use current classloader which has test classes
    dto.setClassLoader(this.getClass().getClassLoader());
    ExtensionBridgeImpl bridge = new ExtensionBridgeImpl(dl);

    // Resolve conventional Impl for SERVICE_IFACE2
    Class<?> impl2 = bridge.getExtensionClass(SERVICE_IFACE2);
    assertNotNull(
        impl2,
        "Expected conventional Impl to be resolved; service="
            + SERVICE_IFACE2
            + ", extId="
            + extId
            + ", loaded="
            + dl.loaded);
    assertEquals(
        "test.ext2.Service2Impl",
        impl2.getName(),
        "Resolved class name mismatch; got=" + (impl2 != null ? impl2.getName() : "null"));
  }

  @Test
  void allowedLoadsAndFindsServiceLoaderProvider() throws Exception {
    PermissionPolicy.get().setAllowPrivileged(true);
    String extId = EXT_ID_BASE + "-spi";
    ExtensionDescriptorDTO dto = baseBuilder(SERVICE_IFACE, extId).build();
    DummyLoader dl = new DummyLoader(dto);
    dto.setClassLoader(this.getClass().getClassLoader());
    ExtensionBridgeImpl bridge = new ExtensionBridgeImpl(dl);

    Class<?> impl = bridge.getExtensionClass(SERVICE_IFACE);
    assertNotNull(
        impl,
        "Expected SPI provider to be resolved; service="
            + SERVICE_IFACE
            + ", extId="
            + extId
            + ", loaded="
            + dl.loaded);
    assertEquals(
        "test.ext.SpiImpl",
        impl.getName(),
        "SPI provider class name mismatch: " + (impl != null ? impl.getName() : "null"));
  }
}
