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
package io.btrace.extcli;

import io.btrace.core.extensions.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

final class ExtensionInspector {
  static ExtensionReport inspect(Path input) throws IOException {
    if (Files.isDirectory(input)) {
      Path dir = input;
      Path api = findFirstDeep(dir, "-api.jar");
      Path impl = findFirstDeep(dir, "-impl.jar");
      if (api == null || impl == null) {
        return ExtensionReport.error("Missing api/impl jars under " + dir);
      }
      String id = readIdFromJar(api);
      if (id == null || id.isEmpty()) {
        String n = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
        id = stripVersionFromName(n);
      }
      return inspectJars(id, api, impl);
    } else if (input.toString().endsWith(".zip")) {
      // Derive id from zip file name first; will prefer manifest id if available later
      String fileName =
          input.getFileName() != null ? input.getFileName().toString() : input.toString();
      String id = stripVersionFromName(fileName.replaceFirst("-extension\\.zip$", ""));
      try (FileSystem fs = FileSystems.newFileSystem(input, (ClassLoader) null)) {
        Path root = fs.getPath("/");
        Path apiIn = findFirstDeep(root, "-api.jar");
        Path implIn = findFirstDeep(root, "-impl.jar");
        if (apiIn == null || implIn == null) {
          return ExtensionReport.error("Missing api/impl jars in zip: " + input);
        }
        Path api = Files.createTempFile("btracex-api-", ".jar");
        Path impl = Files.createTempFile("btracex-impl-", ".jar");
        Files.copy(apiIn, api, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(implIn, impl, StandardCopyOption.REPLACE_EXISTING);
        api.toFile().deleteOnExit();
        impl.toFile().deleteOnExit();
        String manifestId = readIdFromJar(api);
        if (manifestId != null && !manifestId.isEmpty()) id = manifestId;
        return inspectJars(id, api, impl);
      }
    } else {
      return ExtensionReport.error("Invalid input: not a directory or zip: " + input);
    }
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

  private static ExtensionReport inspectJars(String id, Path api, Path impl) throws IOException {
    Set<String> services = readServices(api);
    String version = readVersionFromJar(api);
    if (version == null || version.isEmpty()) {
      // Fallback: parse from API jar filename, e.g., <id>-<version>-api.jar
      String n = api.getFileName() != null ? api.getFileName().toString() : api.toString();
      version = n.replaceFirst("^[^-]+-", "").replaceFirst("-api\\.jar$", "");
    }
    List<String> requiredPerms = readPermissionsFromManifestOrProps(api);
    // Also merge any permissions from impl jar metadata (some extensions declare there)
    List<String> implPerms = readPermissionsFromManifestOrProps(impl);
    if (!implPerms.isEmpty()) {
      LinkedHashSet<String> merged = new LinkedHashSet<>(requiredPerms);
      merged.addAll(implPerms);
      requiredPerms = new ArrayList<>(merged);
    }
    // Also merge any service-level permissions from @ServiceDescriptor on service interfaces
    if (services != null && !services.isEmpty()) {
      try (URLClassLoader cl =
          new URLClassLoader(
              new URL[] {api.toUri().toURL(), impl.toUri().toURL()},
              ExtensionInspector.class.getClassLoader())) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(requiredPerms);
        for (String svc : services) {
          try {
            Class<?> sc = Class.forName(svc, false, cl);
            java.lang.annotation.Annotation sd =
                sc.getAnnotation(io.btrace.core.extensions.ServiceDescriptor.class);
            if (sd instanceof io.btrace.core.extensions.ServiceDescriptor) {
              for (io.btrace.core.extensions.Permission p :
                  ((io.btrace.core.extensions.ServiceDescriptor) sd).permissions()) {
                if (p != null) merged.add(p.name());
              }
            }
          } catch (Throwable ignore) {
          }
        }
        requiredPerms = new ArrayList<>(merged);
      } catch (Throwable ignore) {
      }
    }
    // Derive privileged from the merged permission names. Uppercase with Locale.ROOT: under a
    // Turkish locale the default form maps the 'i' in "reflection" to a dotted capital I, which
    // valueOf rejects - reporting a privileged extension as unprivileged.
    boolean privileged = false;
    for (String n : requiredPerms) {
      try {
        Permission p = Permission.valueOf(n.trim().toUpperCase(Locale.ROOT));
        if (p.isPrivileged()) {
          privileged = true;
          break;
        }
      } catch (IllegalArgumentException ignored) {
        /* skip unknown names */
      }
    }
    return ExtensionReport.ok(id, version, privileged, services, requiredPerms);
  }

  /**
   * Reads the declared service interfaces from the API JAR manifest.
   *
   * <p>The {@code BTrace-Extension-Services} attribute is what the Gradle plugin writes and what
   * {@code ExtensionMetadata} reads when the runtime loads an extension. That reader additionally
   * takes the contents of any {@code META-INF/services/} files in the API JAR, which name
   * implementation classes rather than services; none of the bundled extensions ships one.
   *
   * @param apiJar the extension API JAR
   * @return the declared service class names in manifest order; empty when none are declared
   */
  private static Set<String> readServices(Path apiJar) {
    Set<String> services = new LinkedHashSet<>();
    Attributes attrs = mainAttributes(apiJar);
    if (attrs == null) return services;
    String value = attrs.getValue("BTrace-Extension-Services");
    if (value == null || value.trim().isEmpty()) return services;
    for (String part : value.split(",")) {
      String s = part.trim();
      if (!s.isEmpty()) services.add(s);
    }
    return services;
  }

  private static String readVersionFromJar(Path apiJar) {
    try (JarFile jf = new JarFile(apiJar.toFile())) {
      if (jf.getManifest() != null) {
        String v = jf.getManifest().getMainAttributes().getValue("Implementation-Version");
        if (v != null) return v;
        v = jf.getManifest().getMainAttributes().getValue("BTrace-Extension-Version");
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

  private static String stripVersionFromName(String name) {
    if (name == null || name.isEmpty()) return name;
    // Remove extension suffix if present
    if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
    // Strip trailing version-like segment: last '-' followed by a digit
    int idx = name.lastIndexOf('-');
    if (idx > 0 && idx + 1 < name.length() && Character.isDigit(name.charAt(idx + 1))) {
      return name.substring(0, idx);
    }
    return name;
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

  /**
   * Reads the main manifest attributes of an extension JAR.
   *
   * @param jar the JAR to read
   * @return the main attributes, or {@code null} when the JAR has no readable manifest
   */
  private static Attributes mainAttributes(Path jar) {
    try (JarFile jf = new JarFile(jar.toFile())) {
      Manifest manifest = jf.getManifest();
      return manifest != null ? manifest.getMainAttributes() : null;
    } catch (IOException e) {
      return null;
    }
  }
}
