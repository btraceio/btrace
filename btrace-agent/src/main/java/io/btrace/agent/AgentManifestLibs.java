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
package io.btrace.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
      if (log.isDebugEnabled())
        log.debug("Ignoring manifest libs (btrace.ignoreManifestLibs=true)");
      return new ResolvedLibs(Collections.emptyList(), Collections.emptyList());
    }

    Path agentJarPath = locateAgentPath(anchor);
    Manifest mf = readManifest(agentJarPath);
    if (mf == null) {
      if (log.isDebugEnabled()) log.debug("No manifest found for agent; skipping manifest libs");
      return new ResolvedLibs(Collections.emptyList(), Collections.emptyList());
    }
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

  private static Manifest readManifest(Path agentPath) {
    if (agentPath == null) return null;
    try {
      if (Files.isRegularFile(agentPath)
          && agentPath.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
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
      return Paths.get(uri);
    } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
      if (log.isDebugEnabled()) log.debug("Failed to locate agent path: {}", e.toString());
      return null;
    }
  }

  private static String getAttr(Manifest mf, String key) {
    if (mf.getMainAttributes() == null) return null;
    String v = mf.getMainAttributes().getValue(key);
    return v != null && !v.trim().isEmpty() ? v.trim() : null;
  }

  static void addEntries(Set<Path> out, String value, Path baseDir) {
    if (value == null || value.isEmpty()) return;
    // Space-separated entries (manifest convention)
    String[] parts = value.split("\\s+");
    for (String part : parts) {
      Path p = resolveEntry(part, baseDir);
      if (p != null) out.add(p);
    }
  }

  static Path resolveEntry(String entry, Path baseDir) {
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

  // Deliberately anonymous classes below, not lambdas/method references: this method can run
  // reachable from -javaagent premain(), before the JVM's own java.lang.invoke bootstrap is
  // guaranteed complete. See io.btrace.instr.BootstrapPathIndyFreedomTest and the investigation
  // doc it references.
  static void scanLibTree(Path root, Set<Path> out) {
    if (root == null || !Files.exists(root)) return;
    try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
      stream
          .filter(
              new Predicate<Path>() {
                @Override
                public boolean test(Path p) {
                  return Files.isRegularFile(p);
                }
              })
          .filter(
              new Predicate<Path>() {
                @Override
                public boolean test(Path f) {
                  return f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
                }
              })
          .forEach(
              new Consumer<Path>() {
                @Override
                public void accept(Path p) {
                  out.add(p);
                }
              });
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

  /**
   * Filters {@code entries} to JAR paths that exist and are within {@code home} (unless {@code
   * allowExternal} is {@code true}), then returns them as a list.
   *
   * <p><strong>Ordering contract:</strong> The returned list preserves the iteration order of
   * {@code entries}. Callers pass a {@link java.util.LinkedHashSet} built by processing manifest
   * attributes in declaration order, so the list reflects manifest iteration order. When the same
   * class name is defined in more than one JAR the JVM resolves the <em>first</em> matching entry;
   * callers must therefore ensure that the set passed in is already in the desired precedence order
   * before invoking this method.
   *
   * <p>Package-private for testing.
   */
  static List<Path> filterAndNormalize(Set<Path> entries, Path home, boolean allowExternal) {
    List<Path> out = new ArrayList<>();
    Path realHome = null;
    if (!allowExternal && home != null) {
      try {
        realHome = home.toRealPath();
      } catch (IOException e) {
        if (log.isDebugEnabled())
          log.debug("toRealPath failed for home {}: {}", home, e.getMessage());
        realHome = home.toAbsolutePath().normalize();
      }
    }
    for (Path p : entries) {
      try {
        Path np = p.toAbsolutePath().normalize();
        if (!Files.exists(np)) {
          log.info("Skipping non-existent manifest entry: {}", np);
          continue;
        }
        if (!np.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
          log.info("Skipping non-jar manifest entry: {}", np);
          continue;
        }
        if (!allowExternal && realHome != null) {
          try {
            Path rp = np.toRealPath();
            if (!rp.startsWith(realHome)) {
              log.warn("Rejecting manifest lib outside BTRACE_HOME: {}", rp);
              continue;
            }
          } catch (IOException e) {
            // np.toRealPath() failed; fall back to the pre-resolved realHome (which may itself
            // be a normalized non-canonical path if its toRealPath() failed above).
            if (log.isDebugEnabled()) log.debug("toRealPath failed for {}: {}", np, e.getMessage());
            if (!np.startsWith(realHome)) {
              log.warn(
                  "Rejecting manifest lib outside BTRACE_HOME (symlink resolution failed, using normalized path): {}",
                  np);
              continue;
            }
            log.warn(
                "Symlink resolution failed for manifest lib {}; accepted against normalized BTRACE_HOME only",
                np);
          }
        }
        out.add(np);
      } catch (Exception e) {
        log.warn("Failed resolving manifest entry {}: {}", p, e.toString());
      }
    }
    return out;
  }
}
