package org.openjdk.btrace.extcli.tui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionMeta;
import org.openjdk.btrace.core.extensions.Permission;

final class ExtensionInspectorLite {
  static class Report {
    final String id;
    final String version;
    final boolean privileged;
    final Set<String> services;
    final java.util.List<String> requiredPermNames;

    Report(String id, String version, boolean privileged, Set<String> services, java.util.List<String> requiredPermNames) {
      this.id = id; this.version = version; this.privileged = privileged; this.services = services; this.requiredPermNames = requiredPermNames != null ? requiredPermNames : java.util.Collections.emptyList();
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
    for (String n : perms) { String t = n.trim(); if (!t.isEmpty()) ded.add(t); }
    boolean privileged = computePrivileged(api, impl);
    Set<String> services = readServices(impl);
    return new Report(id, version, privileged, services, new ArrayList<>(ded));
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
      Optional<Path> p = s.filter(pth -> pth.getFileName() != null && pth.getFileName().toString().endsWith(suffix)).findFirst();
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
    } catch (IOException ignored) {}
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
        try (InputStream is = jf.getInputStream(props)) { p.load(is); }
        String id = p.getProperty("extension.id", "");
        if (!id.isEmpty()) return id;
      }
    } catch (IOException ignored) {}
    return "";
  }

  private static Set<String> readServices(Path implJar) {
    Set<String> services = new HashSet<>();
    try (JarFile jf = new JarFile(implJar.toFile())) {
      Enumeration<JarEntry> en = jf.entries();
      while (en.hasMoreElements()) {
        JarEntry e = en.nextElement();
        if (e.getName().startsWith("META-INF/services/") && !e.isDirectory()) {
          services.add(e.getName().substring("META-INF/services/".length()));
        }
      }
    } catch (IOException ignored) {}
    return services;
  }

  private static boolean computePrivileged(Path apiJar, Path implJar) {
    List<String> names = new ArrayList<>();
    names.addAll(readPermissionsFromManifestOrProps(apiJar));
    names.addAll(readPermissionsFromManifestOrProps(implJar));
    for (String n : names) {
      String u = n.trim().toUpperCase();
      if (u.equals("FILE_WRITE") || u.equals("NETWORK") || u.equals("THREADS") || u.equals("NATIVE") || u.equals("EXEC") || u.equals("REFLECTION") || u.equals("CLASSLOADER") || u.equals("UNLIMITED_MEMORY")) {
        return true;
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
          for (String part : v.split(",")) { String s = part.trim(); if (!s.isEmpty()) perms.add(s); }
          return perms;
        }
      }
      JarEntry e = jf.getJarEntry("META-INF/btrace-extension.properties");
      if (e != null) {
        Properties p = new Properties();
        try (InputStream is = jf.getInputStream(e)) { p.load(is); }
        String v = p.getProperty("requires.permissions", "");
        if (!v.isEmpty()) { for (String part : v.split(",")) { String s = part.trim(); if (!s.isEmpty()) perms.add(s); } }
      }
    } catch (IOException ignored) {}
    return perms;
  }
}
