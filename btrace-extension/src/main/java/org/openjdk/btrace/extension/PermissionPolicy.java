package org.openjdk.btrace.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal process-wide policy: per-extension allow/deny plus a global allowPrivileged flag.
 *
 * <p>Used to decide whether an extension implementation may be linked. API classes remain
 * available for SHIMs when implementation is blocked.</p>
 */
public final class PermissionPolicy {
  private static final Logger log = LoggerFactory.getLogger(PermissionPolicy.class);
  private static final PermissionPolicy INSTANCE = new PermissionPolicy();

  public static PermissionPolicy get() {
    return INSTANCE;
  }

  // allow/deny lists (extension IDs) - use thread-safe sets
  private final Set<String> allowExtIds = ConcurrentHashMap.newKeySet();
  private final Set<String> denyExtIds = ConcurrentHashMap.newKeySet();

  // global switch to allow all privileged extensions
  private volatile boolean allowPrivileged = false;

  private PermissionPolicy() {}

  public void setAllowExtensionsCsv(String csv) {
    parseCsvInto(csv, allowExtIds);
  }

  public void setDenyExtensionsCsv(String csv) {
    parseCsvInto(csv, denyExtIds);
  }

  public void setAllowPrivileged(boolean allow) {
    this.allowPrivileged = allow;
  }

  private static void parseCsvInto(String csv, Set<String> target) {
    if (csv == null || csv.trim().isEmpty()) return;
    for (String s : csv.split(",")) {
      String v = s.trim();
      if (!v.isEmpty()) target.add(v);
    }
  }

  public void loadFromDefaults() {
    String source = null;
    try {
      Properties props = new Properties();

      // 1) explicit system property path
      String path = System.getProperty("btrace.permissions");
      if (path != null && !path.isEmpty()) {
        File f = new File(path);
        if (f.exists()) {
          try (InputStream is = new FileInputStream(f)) {
            props.load(is);
            source = f.getAbsolutePath();
          }
        }
      }

      // 2) user home locations
      if (source == null) {
        String home = System.getProperty("user.home");
        if (home != null) {
          File f1 = new File(home, ".btrace/permissions.properties");
          File f2 = new File(home, ".config/btrace/permissions.properties");
          File fx = f1.exists() ? f1 : (f2.exists() ? f2 : null);
          if (fx != null) {
            try (InputStream is = new FileInputStream(fx)) {
              props.load(is);
              source = fx.getAbsolutePath();
            }
          }
        }
      }

      // 3) classpath resource
      if (source == null) {
        InputStream is = ClassLoader.getSystemResourceAsStream("META-INF/btrace/permissions.properties");
        if (is != null) {
          try (InputStream ris = is) {
            props.load(ris);
            source = "classpath:META-INF/btrace/permissions.properties";
          }
        }
      }

      if (source != null) {
        parseProperties(props);
        if (log.isInfoEnabled()) {
          log.info("Loaded BTrace permission policy from {}", source);
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("No BTrace permission policy found; using defaults");
        }
      }
    } catch (Exception e) {
      log.warn("Failed to load BTrace permission policy: {}", e.getMessage());
    }
  }

  private void parseProperties(Properties props) {
    setAllowExtensionsCsv(props.getProperty("allowExtensions", ""));
    setDenyExtensionsCsv(props.getProperty("denyExtensions", ""));
    String ap = props.getProperty("allowPrivileged", "false");
    setAllowPrivileged(Boolean.parseBoolean(ap));
  }

  public boolean isExplicitlyDenied(String extensionId) {
    return extensionId != null && denyExtIds.contains(extensionId);
  }

  public boolean isExplicitlyAllowed(String extensionId) {
    return extensionId != null && allowExtIds.contains(extensionId);
  }

  public boolean isAllowPrivileged() {
    return allowPrivileged;
  }
}
