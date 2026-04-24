package org.openjdk.btrace.extension.processor;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import javax.tools.*;

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
      return diagnostics.stream()
          .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
          .map(Object::toString)
          .collect(Collectors.joining("\n"));
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

    JavaCompiler.CompilationTask task =
        compiler.getTask(null, mgr, diags, options, null, units);
    boolean ok = task.call();
    return new Result(ok, mgr.generatedSources(), diags.getDiagnostics());
  }

  private static final class StringSource extends SimpleJavaFileObject {
    private final String src;

    StringSource(String fqn, String src) {
      super(
          URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
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
                sources.put(className, sw.toString());
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
  }

  private CompileTestHarness() {}
}
