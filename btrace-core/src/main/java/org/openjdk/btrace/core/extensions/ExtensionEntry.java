package org.openjdk.btrace.core.extensions;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class ExtensionEntry {
  private static final ClassLoader NULL_CL = new ClassLoader(null) {};
  private final Map<ClassLoader, ClassLoader> clMap = new WeakHashMap<>();

  private final String id;
  private final String version;
  private final Set<String> exports;
  private final Path jar;

  ExtensionEntry(Path jarFile) throws IOException {
    JarFile jar = new JarFile(jarFile.toFile());
    Manifest manifest = jar.getManifest();
    Attributes attributes = manifest.getMainAttributes();

    this.id = attributes.getValue("BTrace-Extension-ID");
    this.version = attributes.getValue("BTrace-Extension-Version");
    String exportsString = attributes.getValue("BTrace-Extension-Exports");
    this.exports =
        exportsString != null ? new HashSet<>(Arrays.asList(exportsString.split(","))) : null;

    if (id == null || version == null || exports == null) {
      throw new IOException();
    }
    this.jar = jarFile;
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public Set<String> getExports() {
    return exports;
  }

  public ClassLoader getClassLoader(ClassLoader parent) {
    return clMap.computeIfAbsent(parent == null ? NULL_CL : parent, cl -> newClassLoader(parent));
  }

  public Path getJarPath() {
    return jar;
  }

  private ClassLoader newClassLoader(ClassLoader parent) {
    try {
      return new URLClassLoader(new URL[] {jar.toUri().toURL()}, parent);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
