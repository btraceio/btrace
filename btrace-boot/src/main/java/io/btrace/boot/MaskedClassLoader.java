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
package io.btrace.boot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ClassLoader that loads classes from .classdata files in a masked section of a JAR.
 *
 * <p>This classloader reads class bytecode from files stored as .classdata instead of .class,
 * allowing them to be hidden from the bootstrap classloader while remaining in the same JAR.
 *
 * <p>The masked JAR structure has the following sections:
 *
 * <ul>
 *   <li>Bootstrap section: Classes stored as .class files, loaded by bootstrap classloader
 *   <li>Shared section: Classes stored as .classdata files in META-INF/btrace/shared/
 *   <li>Agent section: Classes stored as .classdata files in META-INF/btrace/agent/
 *   <li>Client section: Classes stored as .classdata files in META-INF/btrace/client/
 * </ul>
 *
 * <p>When loading a class, this classloader first checks the specific section (agent or client),
 * then falls back to the shared section, and finally delegates to the parent classloader.
 *
 * @author Jaroslav Bachorik
 */
public final class MaskedClassLoader extends URLClassLoader {
  private static final boolean DEBUG = Boolean.getBoolean("btrace.boot.debug");
  private final JarFile jarFile;
  private final File jarPath;
  private final String sectionPrefix;

  /**
   * Creates a new MaskedClassLoader.
   *
   * @param jarPath path to the JAR file
   * @param section section name ("agent" or "client")
   * @param parent parent classloader
   * @throws IOException if the JAR file cannot be opened
   */
  public MaskedClassLoader(File jarPath, String section, ClassLoader parent) throws IOException {
    // Pass empty URL array to avoid URLClassLoader finding regular .class files from btrace.jar
    // We only want to load from .classdata files (via findClass), delegating all else to parent
    super(new URL[0], parent);
    this.jarPath = jarPath;
    this.jarFile = new JarFile(jarPath);
    this.sectionPrefix = "META-INF/btrace/" + section + "/";
    debug("Created MaskedClassLoader for section: " + section);
  }

  /**
   * Gets the JAR file this classloader is reading from.
   *
   * @return the JAR file
   */
  public JarFile getJarFile() {
    return jarFile;
  }

  /**
   * Gets the path to the JAR file.
   *
   * @return the JAR file path
   */
  public File getJarPath() {
    return jarPath;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    debug("findClass: " + name);

    // Convert class name to path: org.foo.Bar -> META-INF/btrace/agent/org/foo/Bar.classdata
    String classPath = name.replace('.', '/') + ".classdata";

    // Try specific section first (agent or client)
    String path = sectionPrefix + classPath;
    JarEntry entry = jarFile.getJarEntry(path);

    // If not found in specific section, try shared section
    if (entry == null) {
      path = "META-INF/btrace/shared/" + classPath;
      entry = jarFile.getJarEntry(path);
      if (entry != null) {
        debug("Class found in shared section: " + name);
      }
    }

    if (entry == null) {
      debug("Class not found in masked sections: " + name);
      throw new ClassNotFoundException(name);
    }

    try {
      byte[] bytes = readEntry(entry);
      debug("Loaded " + bytes.length + " bytes for class: " + name);

      // Define the class with a CodeSource pointing to the JAR
      URL jarUrl = jarPath.toURI().toURL();
      CodeSource codeSource = new CodeSource(jarUrl, (java.security.cert.Certificate[]) null);
      ProtectionDomain pd = new ProtectionDomain(codeSource, null, this, null);

      return defineClass(name, bytes, 0, bytes.length, pd);
    } catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
  }

  @Override
  public URL findResource(String name) {
    debug("findResource: " + name);

    // First check for the resource in the masked section
    String maskedPath = sectionPrefix + name;
    JarEntry entry = jarFile.getJarEntry(maskedPath);
    if (entry != null) {
      try {
        return createJarEntryURL(maskedPath);
      } catch (MalformedURLException e) {
        debug("Failed to create URL for: " + maskedPath);
      }
    }

    // Fall back to regular resource lookup
    entry = jarFile.getJarEntry(name);
    if (entry != null) {
      try {
        return createJarEntryURL(name);
      } catch (MalformedURLException e) {
        debug("Failed to create URL for: " + name);
      }
    }

    return null;
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    debug("findResources: " + name);

    final URL resource = findResource(name);
    return new Enumeration<URL>() {
      private boolean hasMore = (resource != null);

      @Override
      public boolean hasMoreElements() {
        return hasMore;
      }

      @Override
      public URL nextElement() {
        if (!hasMore) {
          throw new NoSuchElementException();
        }
        hasMore = false;
        return resource;
      }
    };
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    debug("getResourceAsStream: " + name);

    // First check in the masked section
    String maskedPath = sectionPrefix + name;
    JarEntry entry = jarFile.getJarEntry(maskedPath);
    if (entry != null) {
      try {
        return jarFile.getInputStream(entry);
      } catch (IOException e) {
        debug("Failed to get stream for: " + maskedPath);
      }
    }

    // Fall back to regular resource lookup
    entry = jarFile.getJarEntry(name);
    if (entry != null) {
      try {
        return jarFile.getInputStream(entry);
      } catch (IOException e) {
        debug("Failed to get stream for: " + name);
      }
    }

    return null;
  }

  private byte[] readEntry(JarEntry entry) throws IOException {
    try (InputStream is = jarFile.getInputStream(entry)) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        baos.write(buffer, 0, bytesRead);
      }
      return baos.toByteArray();
    }
  }

  private URL createJarEntryURL(String entryPath) throws MalformedURLException {
    // Create a jar: URL for the entry
    return new URL("jar:" + jarPath.toURI().toURL() + "!/" + entryPath);
  }

  @Override
  public void close() throws IOException {
    super.close();
    jarFile.close();
  }

  private void debug(String msg) {
    if (DEBUG) {
      System.err.println("[BTrace Boot] [MaskedCL:" + sectionPrefix + "] " + msg);
    }
  }
}
