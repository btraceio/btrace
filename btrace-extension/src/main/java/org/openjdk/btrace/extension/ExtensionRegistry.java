package org.openjdk.btrace.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal failure registry for extensions discovered/loaded via manifest-based loader.
 *
 * <p>Manifest and extension properties are the single source of truth. Extension discovery,
 * permission checks, and instantiation are handled by the manifest-based bridge/loader. This class
 * only exposes a stable map of failure reasons for diagnostics and UI.
 */
public final class ExtensionRegistry {
  private ExtensionRegistry() {}

  private static final ConcurrentHashMap<String, String> failedExtensions = new ConcurrentHashMap<>();

  public static Map<String, String> getFailedExtensions() {
    return Collections.unmodifiableMap(failedExtensions);
  }

  public static void registerFailedExtension(String idOrName, String reason) {
    failedExtensions.put(idOrName, reason);
  }
}
