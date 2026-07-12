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
package io.btrace.extension.processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * In-memory javac harness. Feeds the processor a set of named source strings and returns the
 * generated source files + diagnostics.
 */
public final class CompileTestHarness {
  public static final class Result {
    public final boolean success;
    public final Map<String, String> generatedSources;
    public final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    Result(
        boolean success,
        Map<String, String> generated,
        List<Diagnostic<? extends JavaFileObject>> diags) {
      this.success = success;
      this.generatedSources = generated;
      this.diagnostics = diags;
    }

    public String errors() {
      return formatErrors(diagnostics);
    }
  }

  public static Result compile(Map<String, String> sources) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No JavaCompiler available — run tests on a JDK, not JRE");
    }
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    StandardJavaFileManager std =
        compiler.getStandardFileManager(diags, Locale.ROOT, StandardCharsets.UTF_8);
    InMemoryFileManager mgr = new InMemoryFileManager(std);

    List<JavaFileObject> units = new ArrayList<>();
    for (Map.Entry<String, String> e : sources.entrySet()) {
      units.add(new StringSource(e.getKey(), e.getValue()));
    }

    String cp = System.getProperty("java.class.path");
    List<String> options =
        Arrays.asList("-classpath", cp, "-processor", ExternalTypeProcessor.class.getName());

    JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, diags, options, null, units);
    boolean ok = task.call();
    return new Result(ok, mgr.generatedSources(), diags.getDiagnostics());
  }

  public static final class RunnableResult {
    public final boolean success;
    public final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    public final ClassLoader loader;
    public final Map<String, byte[]> classBytes;

    RunnableResult(
        boolean success,
        List<Diagnostic<? extends JavaFileObject>> diags,
        ClassLoader loader,
        Map<String, byte[]> classBytes) {
      this.success = success;
      this.diagnostics = diags;
      this.loader = loader;
      this.classBytes = classBytes;
    }

    public String errors() {
      return formatErrors(diagnostics);
    }
  }

  static String formatErrors(List<Diagnostic<? extends JavaFileObject>> diags) {
    return diags.stream()
        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
        .map(Object::toString)
        .collect(Collectors.joining("\n"));
  }

  public static RunnableResult compileAndLoad(Map<String, String> sources) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No JavaCompiler available — run tests on a JDK, not JRE");
    }
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    StandardJavaFileManager std =
        compiler.getStandardFileManager(diags, Locale.ROOT, StandardCharsets.UTF_8);
    InMemoryFileManager mgr = new InMemoryFileManager(std);

    List<JavaFileObject> units = new ArrayList<>();
    for (Map.Entry<String, String> e : sources.entrySet()) {
      units.add(new StringSource(e.getKey(), e.getValue()));
    }
    List<String> options =
        Arrays.asList(
            "-classpath", System.getProperty("java.class.path"),
            "-processor", ExternalTypeProcessor.class.getName());

    JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, diags, options, null, units);
    boolean ok = task.call();
    if (!ok) return new RunnableResult(false, diags.getDiagnostics(), null, new LinkedHashMap<>());

    ClassLoader loader =
        new ClassLoader(CompileTestHarness.class.getClassLoader()) {
          @Override
          protected Class<?> findClass(String name) throws ClassNotFoundException {
            ByteArrayOutputStream baos = mgr.bytes.get(name);
            if (baos == null) throw new ClassNotFoundException(name);
            byte[] b = baos.toByteArray();
            return defineClass(name, b, 0, b.length);
          }
        };
    return new RunnableResult(true, diags.getDiagnostics(), loader, mgr.classBytes());
  }

  static final class StringSource extends SimpleJavaFileObject {
    private final String src;

    StringSource(String fqn, String src) {
      super(URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.src = src;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return src;
    }
  }

  static final class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    final Map<String, ByteArrayOutputStream> bytes = new LinkedHashMap<>();
    private final Map<String, String> sources = new LinkedHashMap<>();

    InMemoryFileManager(JavaFileManager m) {
      super(m);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
        Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
      if (kind == JavaFileObject.Kind.SOURCE) {
        return new SimpleJavaFileObject(
            URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
          private String content = null;

          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content != null ? content : "";
          }

          @Override
          public Writer openWriter() {
            StringWriter sw = new StringWriter();
            return new Writer() {
              @Override
              public void write(char[] cbuf, int off, int len) {
                sw.write(cbuf, off, len);
              }

              @Override
              public void flush() {}

              @Override
              public void close() {
                content = sw.toString();
                sources.put(className, content);
              }
            };
          }
        };
      }
      ByteArrayOutputStream baos =
          bytes.computeIfAbsent(className, k -> new ByteArrayOutputStream());
      return new SimpleJavaFileObject(
          URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
        @Override
        public OutputStream openOutputStream() {
          return baos;
        }
      };
    }

    Map<String, String> generatedSources() {
      return sources;
    }

    Map<String, byte[]> classBytes() {
      Map<String, byte[]> result = new LinkedHashMap<>();
      bytes.forEach((name, content) -> result.put(name, content.toByteArray()));
      return result;
    }
  }

  private CompileTestHarness() {}
}
