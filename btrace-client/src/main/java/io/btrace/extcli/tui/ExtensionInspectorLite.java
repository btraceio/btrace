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
package io.btrace.extcli.tui;

import io.btrace.core.extensions.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

final class ExtensionInspectorLite {
  static class Report {
    final String id;
    final String version;
    final boolean privileged;
    final java.util.List<String> requiredPermNames;

    Report(
        String id, String version, boolean privileged, java.util.List<String> requiredPermNames) {
      this.id = id;
      this.version = version;
      this.privileged = privileged;
      this.requiredPermNames =
          requiredPermNames != null ? requiredPermNames : java.util.Collections.emptyList();
    }
  }

  static Report inspectDirectory(Path dir) throws IOException {
    Path api = findFirstDeep(dir, "-api.jar");
    Path impl = findFirstDeep(dir, "-impl.jar");
    if (api == null || impl == null) throw new IOException("Missing api/impl jars under " + dir);

    String id = readIdFromJar(api);
    if (id == null || id.isEmpty()) {
      String n = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
      id = stripVersionFromName(n);
    }
    String version = readVersionFromJar(api);
    if (version == null || version.isEmpty()) {
      String n = api.getFileName() != null ? api.getFileName().toString() : api.toString();
      version = n.replaceFirst("^[^-]+-", "").replaceFirst("-api\\.jar$", "");
    }
    List<String> perms = new ArrayList<>();
    perms.addAll(readPermissionsFromManifestOrProps(api));
    perms.addAll(readPermissionsFromManifestOrProps(impl));
    LinkedHashSet<String> ded = new LinkedHashSet<>();
    for (String n : perms) {
      String t = n.trim();
      if (!t.isEmpty()) ded.add(t);
    }
    boolean privileged = computePrivileged(api, impl);
    return new Report(id, version, privileged, new ArrayList<>(ded));
  }

  private static String stripVersionFromName(String name) {
    if (name == null || name.isEmpty()) return name;
    int idx = name.lastIndexOf('-');
    if (idx > 0 && idx + 1 < name.length() && Character.isDigit(name.charAt(idx + 1))) {
      return name.substring(0, idx);
    }
    return name;
  }

  private static Path findFirstDeep(Path root, String suffix) throws IOException {
    try (Stream<Path> s = Files.walk(root)) {
      Optional<Path> p =
          s.filter(
                  pth -> pth.getFileName() != null && pth.getFileName().toString().endsWith(suffix))
              .findFirst();
      return p.orElse(null);
    }
  }

  private static String readVersionFromJar(Path apiJar) {
    try (JarFile jf = new JarFile(apiJar.toFile())) {
      Manifest mf = jf.getManifest();
      if (mf != null) {
        String v = mf.getMainAttributes().getValue("Implementation-Version");
        if (v != null) return v;
        v = mf.getMainAttributes().getValue("BTrace-Extension-Version");
        if (v != null) return v;
      }
    } catch (IOException ignored) {
    }
    return "";
  }

  private static String readIdFromJar(Path jarPath) {
    try (JarFile jf = new JarFile(jarPath.toFile())) {
      Manifest mf = jf.getManifest();
      if (mf != null) {
        String id = mf.getMainAttributes().getValue("BTrace-Extension-Id");
        if (id != null && !id.isEmpty()) return id;
      }
      JarEntry props = jf.getJarEntry("META-INF/btrace-extension.properties");
      if (props != null) {
        Properties p = new Properties();
        try (InputStream is = jf.getInputStream(props)) {
          p.load(is);
        }
        String id = p.getProperty("extension.id", "");
        if (!id.isEmpty()) return id;
      }
    } catch (IOException ignored) {
    }
    return "";
  }

  private static boolean computePrivileged(Path apiJar, Path implJar) {
    List<String> names = new ArrayList<>();
    names.addAll(readPermissionsFromManifestOrProps(apiJar));
    names.addAll(readPermissionsFromManifestOrProps(implJar));
    for (String n : names) {
      String u = n.trim().toUpperCase(Locale.ROOT);
      try {
        if (Permission.valueOf(u).isPrivileged()) {
          return true;
        }
      } catch (IllegalArgumentException unknownPermission) {
        // Permission names this build does not know are ignored, as before.
      }
    }
    return false;
  }

  private static List<String> readPermissionsFromManifestOrProps(Path jarPath) {
    List<String> perms = new ArrayList<>();
    try (JarFile jf = new JarFile(jarPath.toFile())) {
      Manifest mf = jf.getManifest();
      if (mf != null) {
        String v = mf.getMainAttributes().getValue("BTrace-Extension-Permissions");
        if (v != null && !v.trim().isEmpty()) {
          for (String part : v.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) perms.add(s);
          }
          return perms;
        }
      }
      JarEntry e = jf.getJarEntry("META-INF/btrace-extension.properties");
      if (e != null) {
        Properties p = new Properties();
        try (InputStream is = jf.getInputStream(e)) {
          p.load(is);
        }
        String v = p.getProperty("requires.permissions", "");
        if (!v.isEmpty()) {
          for (String part : v.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) perms.add(s);
          }
        }
      }
    } catch (IOException ignored) {
    }
    return perms;
  }
}
