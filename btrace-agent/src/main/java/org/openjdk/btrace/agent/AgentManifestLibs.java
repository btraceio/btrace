/*
 * Utility to read agent manifest attributes and resolve library paths
 * to be appended to the bootstrap and system classpaths.
 */
package org.openjdk.btrace.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AgentManifestLibs {
  private static final Logger log = LoggerFactory.getLogger(AgentManifestLibs.class);

  // Custom BTrace manifest attributes
  private static final String ATTR_BTRACE_BOOT_LIBS = "BTrace-Boot-Libs";
  private static final String ATTR_BTRACE_SYSTEM_LIBS = "BTrace-System-Libs";
  private static final String ATTR_BTRACE_LIBS_ROOT = "BTrace-Libs-Root";
  private static final String ATTR_BTRACE_LIBS_PROFILE = "BTrace-Libs-Profile";
  // Standard attribute the JVM also understands; we read to unify behavior
  private static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";

  static final class ResolvedLibs {
    final List<Path> bootJars;
    final List<Path> systemJars;

    ResolvedLibs(List<Path> bootJars, List<Path> systemJars) {
      this.bootJars = bootJars;
      this.systemJars = systemJars;
    }
  }

  private AgentManifestLibs() {}

  static ResolvedLibs resolveFromManifest(Class<?> anchor) {
    boolean ignore = Boolean.getBoolean("btrace.ignoreManifestLibs");
    if (ignore) {
      if (log.isDebugEnabled()) log.debug("Ignoring manifest libs (btrace.ignoreManifestLibs=true)");
      return new ResolvedLibs(Collections.emptyList(), Collections.emptyList());
    }

    Manifest mf = readManifest(anchor);
    if (mf == null) {
      if (log.isDebugEnabled()) log.debug("No manifest found for agent; skipping manifest libs");
      return new ResolvedLibs(Collections.emptyList(), Collections.emptyList());
    }

    Path agentJarPath = locateAgentPath(anchor);
    Path baseDir = agentJarPath != null ? agentJarPath.getParent() : null;
    // Default libs root: BTRACE_HOME/libs if the agent is under .../libs/btrace-agent.jar
    Path libsRoot = resolveLibsRoot(mf, baseDir);

    Set<Path> boot = new LinkedHashSet<>();
    Set<Path> sys = new LinkedHashSet<>();

    // 1) Standard Boot-Class-Path
    addEntries(boot, getAttr(mf, ATTR_BOOT_CLASS_PATH), baseDir);
    // 2) Custom BTrace attributes
    addEntries(boot, getAttr(mf, ATTR_BTRACE_BOOT_LIBS), baseDir);
    addEntries(sys, getAttr(mf, ATTR_BTRACE_SYSTEM_LIBS), baseDir);

    // 3) Optional profile scan
    String profile = getAttr(mf, ATTR_BTRACE_LIBS_PROFILE);
    if (profile != null && libsRoot != null) {
      Path profileRoot = libsRoot.resolve(profile);
      scanLibTree(profileRoot.resolve("boot"), boot);
      scanLibTree(profileRoot.resolve("system"), sys);
    }

    // Safety: by default restrict to agent home unless explicitly allowed
    boolean allowExternal = Boolean.getBoolean("btrace.allowExternalLibs");
    Path home = tryComputeBTraceHome(agentJarPath);
    List<Path> bootList = filterAndNormalize(boot, home, allowExternal);
    List<Path> sysList = filterAndNormalize(sys, home, allowExternal);

    if (log.isDebugEnabled()) {
      log.debug("Manifest-resolved boot libs: {}", bootList);
      log.debug("Manifest-resolved system libs: {}", sysList);
    }
    return new ResolvedLibs(bootList, sysList);
  }

  private static Manifest readManifest(Class<?> anchor) {
    try {
      Path agentPath = locateAgentPath(anchor);
      if (agentPath == null) return null;
      if (Files.isRegularFile(agentPath) && agentPath.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
        try (JarFile jf = new JarFile(agentPath.toFile())) {
          return jf.getManifest();
        }
      }
      // exploded directory
      Path mf = agentPath.resolve("META-INF").resolve("MANIFEST.MF");
      if (Files.exists(mf)) {
        try (FileInputStream fis = new FileInputStream(mf.toFile())) {
          return new Manifest(fis);
        }
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Failed to read manifest: {}", e.toString());
    }
    return null;
  }

  private static Path locateAgentPath(Class<?> anchor) {
    try {
      URL url = anchor.getProtectionDomain().getCodeSource().getLocation();
      if (url == null) return null;
      URI uri = url.toURI();
      Path p = Paths.get(uri);
      if (Files.isDirectory(p)) {
        return p; // exploded
      }
      return p;
    } catch (URISyntaxException e) {
      if (log.isDebugEnabled()) log.debug("Failed to locate agent path: {}", e.toString());
      return null;
    }
  }

  private static String getAttr(Manifest mf, String key) {
    if (mf.getMainAttributes() == null) return null;
    String v = mf.getMainAttributes().getValue(key);
    return v != null && !v.trim().isEmpty() ? v.trim() : null;
  }

  private static void addEntries(Set<Path> out, String value, Path baseDir) {
    if (value == null || value.isEmpty()) return;
    // Space-separated entries (manifest convention)
    String[] parts = value.split("\\s+");
    for (String part : parts) {
      Path p = resolveEntry(part, baseDir);
      if (p != null) out.add(p);
    }
  }

  private static Path resolveEntry(String entry, Path baseDir) {
    try {
      if (entry.startsWith("file:")) {
        return Paths.get(new URI(entry));
      }
    } catch (Exception ignored) {
      // fallthrough to path resolution
    }
    Path p = Paths.get(entry);
    if (!p.isAbsolute() && baseDir != null) {
      p = baseDir.resolve(p).normalize();
    }
    return p;
  }

  private static void scanLibTree(Path root, Set<Path> out) {
    try {
      if (root == null || !Files.exists(root)) return;
      Files.walk(root)
          .filter(f -> Files.isRegularFile(f))
          .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
          .forEach(out::add);
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Failed to scan libs at {}: {}", root, e.toString());
    }
  }

  private static Path resolveLibsRoot(Manifest mf, Path baseDir) {
    String root = getAttr(mf, ATTR_BTRACE_LIBS_ROOT);
    if (root != null) {
      Path p = resolveEntry(root, baseDir);
      if (p != null) return p;
    }
    if (baseDir == null) return null;
    // If agent is at .../libs/btrace-agent.jar, prefer .../btrace-libs
    File parent = baseDir.toFile();
    if (parent.getName().equals("libs")) {
      return parent.getParentFile() != null
          ? parent.getParentFile().toPath().resolve("btrace-libs")
          : baseDir.resolve("..").resolve("btrace-libs").normalize();
    }
    return baseDir.resolve("btrace-libs");
  }

  private static Path tryComputeBTraceHome(Path agentJar) {
    if (agentJar == null) return null;
    File f = agentJar.toFile();
    File parent = f.getParentFile();
    if (parent != null && parent.getName().equals("libs")) {
      File home = parent.getParentFile();
      if (home != null) return home.toPath();
    }
    // fallback: parent dir of agent JAR
    return parent != null ? parent.toPath() : null;
  }

  private static List<Path> filterAndNormalize(Set<Path> entries, Path home, boolean allowExternal) {
    List<Path> out = new ArrayList<>();
    for (Path p : entries) {
      try {
        Path np = p.toAbsolutePath().normalize();
        if (!Files.exists(np)) {
          if (log.isDebugEnabled()) log.debug("Skipping non-existent manifest entry: {}", np);
          continue;
        }
        if (!np.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
          if (log.isDebugEnabled()) log.debug("Skipping non-jar manifest entry: {}", np);
          continue;
        }
        if (!allowExternal && home != null) {
          try {
            Path hp = home.toRealPath();
            Path rp = np.toRealPath();
            if (!rp.startsWith(hp)) {
              log.warn("Rejecting manifest lib outside BTRACE_HOME: {}", rp);
              continue;
            }
          } catch (IOException ignored) {
            // best effort; fall back to np.startsWith(home)
            if (!np.startsWith(home)) {
              log.warn("Rejecting manifest lib outside BTRACE_HOME: {}", np);
              continue;
            }
          }
        }
        out.add(np);
      } catch (Exception e) {
        if (log.isDebugEnabled()) log.debug("Failed resolving manifest entry {}: {}", p, e.toString());
      }
    }
    return out;
  }
}

