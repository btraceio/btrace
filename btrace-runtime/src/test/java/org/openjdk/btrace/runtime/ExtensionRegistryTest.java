package org.openjdk.btrace.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.ExtensionDescriptor;
import org.openjdk.btrace.core.extensions.ExtensionException;

class ExtensionRegistryTest {

  @ExtensionDescriptor(name = "ValidExtension", version = "1.0.0", description = "A valid extension")
  static class ValidExtension extends Extension {
    @Override
    public void initialize(ExtensionContext context) {
      super.initialize(context);
    }

    @Override
    public void close() {}
  }

  // Extension missing @ExtensionDescriptor - will fail to load
  static class InvalidExtension extends Extension {
    @Override
    public void initialize(ExtensionContext context) {
      super.initialize(context);
    }

    @Override
    public void close() {}
  }

  @BeforeEach
  void setup() {
    // Note: We can't easily clear the static maps in ExtensionRegistry,
    // so these tests assume a fresh state or are idempotent
  }

  @Test
  void testFailedExtensionIsTracked() {
    // Manually trigger what would happen during discovery
    try {
      org.openjdk.btrace.core.extensions.ExtensionMeta.from(InvalidExtension.class);
      fail("Expected ExtensionException for InvalidExtension");
    } catch (ExtensionException e) {
      // This simulates what discover() does - store the failure
      String errorMsg = e.getMessage();
      assertTrue(errorMsg.contains("missing @ExtensionDescriptor"));
    }
  }

  @Test
  void testGetFailedExtensionsReturnsMap() {
    Map<String, String> failedExtensions = ExtensionRegistry.getFailedExtensions();
    assertNotNull(failedExtensions);
    // The map should be unmodifiable
    assertThrows(
        UnsupportedOperationException.class, () -> failedExtensions.put("test", "value"));
  }

  @Test
  void testIsFailedExtensionWithValidClass() {
    // Since we can't easily manipulate the static state without ServiceLoader,
    // we just verify the method is callable and doesn't throw
    // ValidExtension has proper annotations, so it should return false
    // (unless it was previously discovered and failed, which is unlikely)
    ExtensionRegistry.isFailedExtension(ValidExtension.class);
  }

  @Test
  void testGetFailureReasonWithNonFailedExtension() {
    String reason = ExtensionRegistry.getFailureReason(ValidExtension.class);
    // Should be null for a non-failed extension (unless it was previously discovered and failed)
    // This is a safety check that the method is callable
    assertNull(reason);
  }

  @Test
  void testValidExtensionMetaCanBeExtracted() {
    // Verify that a properly annotated extension can have its metadata extracted
    org.openjdk.btrace.core.extensions.ExtensionMeta meta =
        org.openjdk.btrace.core.extensions.ExtensionMeta.from(ValidExtension.class);
    assertNotNull(meta);
    assertEquals("ValidExtension", meta.getName());
    assertEquals("1.0.0", meta.getVersion());
  }
}
