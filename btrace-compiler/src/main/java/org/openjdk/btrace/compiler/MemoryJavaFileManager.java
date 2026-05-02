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
package org.openjdk.btrace.compiler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

/**
 * JavaFileManager that keeps compiled .class bytes in memory. And also can expose input .java
 * "files" from Strings.
 *
 * @author A. Sundararajan
 */
@SuppressWarnings("RedundantThrows")
public final class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

  private final List<String> includeDirs;
  private Map<String, byte[]> classBytes;

  public MemoryJavaFileManager(JavaFileManager fileManager, List<String> includeDirs) {
    super(fileManager);
    this.includeDirs = includeDirs;
    classBytes = new HashMap<>();
  }

  static JavaFileObject preprocessedFileObject(JavaFileObject fo, List<String> includeDirs)
      throws IOException {
    if (includeDirs != null) {
      StringWriter out = new StringWriter();
      PCPP pcpp = new PCPP(includeDirs, out);
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(fo.openInputStream(), StandardCharsets.UTF_8));
      pcpp.run(reader, fo.getName());
      return new StringInputBuffer(fo.getName(), out.toString());
    } else {
      return fo;
    }
  }

  static JavaFileObject makeStringSource(String name, String code, List<String> includeDirs) {
    if (includeDirs != null) {
      StringWriter out = new StringWriter();
      PCPP pcpp = new PCPP(includeDirs, out);
      try {
        pcpp.run(new StringReader(code), name);
      } catch (IOException exp) {
        throw new RuntimeException(exp);
      }
      return new StringInputBuffer(name, out.toString());
    } else {
      return new StringInputBuffer(name, code);
    }
  }

  static URI toURI(String name) {
    File file = new File(name);
    if (file.exists()) {
      return file.toURI();
    } else {
      try {
        return URI.create("mfm:///" + name);
      } catch (Exception exp) {
        return URI.create("mfm:///org/openjdk/btrace/script/java/java_source");
      }
    }
  }

  public Map<String, byte[]> getClassBytes() {
    return classBytes;
  }

  @Override
  public void close() throws IOException {
    classBytes = new HashMap<>();
  }

  @Override
  public void flush() throws IOException {}

  @Override
  public JavaFileObject getJavaFileForOutput(
      JavaFileManager.Location location, String className, Kind kind, FileObject sibling)
      throws IOException {
    if (kind == Kind.CLASS) {
      return new ClassOutputBuffer(className);
    } else {
      return super.getJavaFileForOutput(location, className, kind, sibling);
    }
  }

  @Override
  public JavaFileObject getJavaFileForInput(
      JavaFileManager.Location location, String className, Kind kind) throws IOException {
    JavaFileObject result = super.getJavaFileForInput(location, className, kind);
    if (kind == Kind.SOURCE) {
      return preprocessedFileObject(result, includeDirs);
    } else {
      return result;
    }
  }

  /** A file object used to represent Java source coming from a string. */
  private static class StringInputBuffer extends SimpleJavaFileObject {

    final String code;

    StringInputBuffer(String name, String code) {
      super(toURI(name), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
      return CharBuffer.wrap(code);
    }

    public Reader openReader() {
      return new StringReader(code);
    }
  }

  /** A file object that stores Java bytecode into the classBytes map. */
  private class ClassOutputBuffer extends SimpleJavaFileObject {

    private final String name;

    ClassOutputBuffer(String name) {
      super(toURI(name), Kind.CLASS);
      this.name = name;
    }

    @Override
    public OutputStream openOutputStream() {
      return new FilterOutputStream(new ByteArrayOutputStream()) {

        @Override
        public void close() throws IOException {
          out.close();
          ByteArrayOutputStream bos = (ByteArrayOutputStream) out;
          classBytes.put(name, bos.toByteArray());
        }
      };
    }
  }
}
