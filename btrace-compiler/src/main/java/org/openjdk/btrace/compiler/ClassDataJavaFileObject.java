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
package io.btrace.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

/**
 * A JavaFileObject that reads class bytecode from .classdata files in a masked JAR.
 *
 * <p>This is used by MaskedJavaFileManager to provide javac with access to annotation classes
 * stored as .classdata files instead of .class files.
 *
 * @author Jaroslav Bachorik
 */
class ClassDataJavaFileObject implements JavaFileObject {
  private final String className;
  private final JarFile jarFile;
  private final JarEntry entry;
  private final String entryName;
  private final URI uri;

  /**
   * Creates a new ClassDataJavaFileObject.
   *
   * @param className the fully qualified class name (e.g., "org.example.MyClass")
   * @param jarFile the JAR file containing the .classdata file
   * @param entry the JAR entry for the .classdata file
   */
  ClassDataJavaFileObject(String className, JarFile jarFile, JarEntry entry) {
    String name = entry.getName();
    if (name.contains("..")) {
      throw new IllegalArgumentException("Invalid entry name (path traversal): " + name);
    }
    this.className = className;
    this.jarFile = jarFile;
    this.entry = entry;
    this.entryName = name;
    this.uri = URI.create("jar:file:" + jarFile.getName() + "!/" + name);
  }

  @Override
  public Kind getKind() {
    return Kind.CLASS;
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    if (kind != Kind.CLASS) {
      return false;
    }
    String baseName = simpleName + ".class";
    return className.equals(simpleName) || className.endsWith("." + simpleName);
  }

  @Override
  public NestingKind getNestingKind() {
    return null; // Unknown
  }

  @Override
  public Modifier getAccessLevel() {
    return null; // Unknown
  }

  @Override
  public URI toUri() {
    return uri;
  }

  @Override
  public String getName() {
    // Return a .class name instead of .classdata for javac compatibility
    if (entryName.endsWith(".classdata")) {
      return entryName.substring(0, entryName.length() - ".classdata".length()) + ".class";
    }
    return entryName;
  }

  @Override
  public InputStream openInputStream() throws IOException {
    return jarFile.getInputStream(entry);
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    throw new UnsupportedOperationException("Cannot write to .classdata file");
  }

  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException("Cannot read .classdata as text");
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException("Cannot read .classdata as text");
  }

  @Override
  public Writer openWriter() throws IOException {
    throw new UnsupportedOperationException("Cannot write to .classdata file");
  }

  @Override
  public long getLastModified() {
    return entry.getTime();
  }

  @Override
  public boolean delete() {
    return false;
  }

  /**
   * Reads the entire .classdata file into a byte array.
   *
   * @return the class bytecode
   * @throws IOException if reading fails
   */
  byte[] getClassBytes() throws IOException {
    try (InputStream is = openInputStream()) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        baos.write(buffer, 0, bytesRead);
      }
      return baos.toByteArray();
    }
  }

  /**
   * Infers the binary name (fully qualified class name) from this file object.
   *
   * @return the binary name (e.g., "io.btrace.core.annotations.BTrace")
   */
  String inferBinaryName() {
    return className;
  }
}
