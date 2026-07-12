# `@ExternalType` Adapter Generator Implementation Plan

> **Historical status (2026-07-12):** The implementation tasks in this plan were completed. The
> original paths use the pre-consolidation `org.openjdk.btrace` layout; the processor and provided
> extension helpers now live in `btrace-core` under `io.btrace`. The generated adapters use a
> classloader-safe `ClassValue` cache rather than the original single per-method handle cache.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `@ExternalType` annotation + build-time APT that generates typed, lazy-resolution adapter classes for calling application-provided types from extension impls — eliminating hand-written reflective adapters while preserving the provided-style philosophy (API on bootstrap, impl reflective against app types via TCCL).

**Architecture:** A new `btrace-extension-processor` module contains the JSR-269 annotation processor. `@ExternalType` and nested `@ExternalType.Static` live in `btrace-core` so they're visible both to extension API modules and to authors using the processor. The Gradle extension plugin registers the processor on the `api` source set so generated adapters land in the API jar alongside the interfaces they were generated from. Generated classes use per-method `volatile MethodHandle` fields with lazy double-checked resolution via `MethodHandles.publicLookup()` + the receiver's defining loader (for virtual methods) or TCCL (for static methods). No bytecode rewriting: the generator emits plain Java source compiled by javac.

**Tech stack:** JSR 269 annotation processor (`javax.annotation.processing`), Java source generation via `Filer`, in-memory compile tests using `ToolProvider.getSystemJavaCompiler()` + `SimpleJavaFileObject`. Existing `MethodHandleCache` + `ClassLoadingUtil` from `btrace-extension`.

**Worktree:** `/Users/jbachorik/src/btrace-external-type` on branch `jb/external-type-adapter` (based on `jb/configurations`).

---

## File Map

### Created

- `btrace-core/src/main/java/org/openjdk/btrace/core/extensions/ExternalType.java` — the annotation + nested `Static`.
- `btrace-extension-processor/build.gradle` — module build.
- `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java` — APT entry point.
- `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterSpec.java` — value type: interface FQN, target FQN, list of `MethodSpec`.
- `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/MethodSpec.java` — value type: name, return type, param types, static flag.
- `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterEmitter.java` — renders `AdapterSpec` → Java source.
- `btrace-extension-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor` — service registration.
- `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java` — compile-test harness.
- `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/CompileTestHarness.java` — `JavaCompiler` wrapper (in-memory sources + in-memory output).
- `integration-tests/src/test/java/org/openjdk/btrace/itests/ExternalTypeAdapterIT.java` — end-to-end: annotated interface → generated adapter → invoke against a dummy class via TCCL.

### Modified

- `settings.gradle` — add `include ':btrace-extension-processor'`.
- `btrace-gradle-plugin/src/main/groovy/org/openjdk/btrace/gradle/BTraceExtensionPlugin.groovy` — register processor on `api` source set.
- `docs/architecture/provided-style-extensions.md` — add "External Type Adapters" section replacing hand-written reflective example.

---

## Task 1: Add `@ExternalType` annotation

**Files:**
- Create: `btrace-core/src/main/java/org/openjdk/btrace/core/extensions/ExternalType.java`
- Test: `btrace-core/src/test/java/org/openjdk/btrace/core/extensions/ExternalTypeAnnotationTest.java`

- [ ] **Step 1: Write the failing test**

Create `btrace-core/src/test/java/org/openjdk/btrace/core/extensions/ExternalTypeAnnotationTest.java`:

```java
package org.openjdk.btrace.core.extensions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.*;

class ExternalTypeAnnotationTest {
  @ExternalType("com.example.App")
  interface FakeApi {
    @ExternalType.Static
    Object create(String name);
    int counter();
  }

  @Test
  void annotationValuePreserved() {
    ExternalType a = FakeApi.class.getAnnotation(ExternalType.class);
    assertNotNull(a);
    assertEquals("com.example.App", a.value());
  }

  @Test
  void staticAnnotationOnMethod() throws NoSuchMethodException {
    assertNotNull(
        FakeApi.class.getDeclaredMethod("create", String.class)
            .getAnnotation(ExternalType.Static.class));
    assertNull(
        FakeApi.class.getDeclaredMethod("counter").getAnnotation(ExternalType.Static.class));
  }

  @Test
  void retentionIsRuntime() {
    Retention r = ExternalType.class.getAnnotation(Retention.class);
    assertEquals(RetentionPolicy.RUNTIME, r.value());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :btrace-core:test --tests ExternalTypeAnnotationTest`
Expected: FAIL — `ExternalType` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `btrace-core/src/main/java/org/openjdk/btrace/core/extensions/ExternalType.java`:

```java
package org.openjdk.btrace.core.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as the build-time contract for an <em>external</em> application type — one
 * whose class is not available at the extension's compile/link time and must be resolved at run
 * time via the application's class loader.
 *
 * <p>When combined with the {@code @ExternalType} annotation processor, a companion adapter class
 * named {@code <InterfaceSimpleName>$Ext} is generated in the same package with static dispatchers
 * for each declared method. Dispatchers lazily resolve the target class and method via
 * {@link java.lang.invoke.MethodHandles#publicLookup()} and cache the resulting
 * {@link java.lang.invoke.MethodHandle}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExternalType {
  /** Fully-qualified name of the external type this interface adapts. */
  String value();

  /**
   * Marks an interface method as a static method on the external type. Without this annotation,
   * methods are resolved with {@code findVirtual} and dispatched against the receiver passed as the
   * first adapter argument.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface Static {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :btrace-core:test --tests ExternalTypeAnnotationTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add btrace-core/src/main/java/org/openjdk/btrace/core/extensions/ExternalType.java \
        btrace-core/src/test/java/org/openjdk/btrace/core/extensions/ExternalTypeAnnotationTest.java
git commit -m "feat(core): add @ExternalType annotation for app-type adapter generation"
```

---

## Task 2: Scaffold `btrace-extension-processor` module

**Files:**
- Create: `btrace-extension-processor/build.gradle`
- Create: `btrace-extension-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
- Create: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`
- Modify: `settings.gradle`

- [ ] **Step 1: Register module in `settings.gradle`**

Append to `settings.gradle` (add alongside existing `include` lines, alphabetical order):

```groovy
include ':btrace-extension-processor'
```

- [ ] **Step 2: Write module build.gradle**

Create `btrace-extension-processor/build.gradle`:

```groovy
apply from: "$rootDir/common.gradle"

dependencies {
    implementation project(':btrace-core')

    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Service registration**

Create `btrace-extension-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor` with single line:

```
org.openjdk.btrace.extension.processor.ExternalTypeProcessor
```

- [ ] **Step 4: Stub processor**

Create `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`:

```java
package org.openjdk.btrace.extension.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.openjdk.btrace.core.extensions.ExternalType")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class ExternalTypeProcessor extends AbstractProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return false;
  }
}
```

- [ ] **Step 5: Verify module compiles**

Run: `./gradlew :btrace-extension-processor:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle btrace-extension-processor/
git commit -m "feat(ext-processor): scaffold module for @ExternalType annotation processor"
```

---

## Task 3: Compile-test harness

**Files:**
- Create: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/CompileTestHarness.java`

This harness is shared by all subsequent tests; build it once, use it everywhere.

- [ ] **Step 1: Write the harness**

Create `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/CompileTestHarness.java`:

```java
package org.openjdk.btrace.extension.processor;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory javac harness. Feeds the processor a set of named source strings and returns the
 * generated source files + diagnostics.
 */
public final class CompileTestHarness {
  public static final class Result {
    public final boolean success;
    public final Map<String, String> generatedSources;  // FQN -> source
    public final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    Result(boolean success, Map<String, String> generated,
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
    StandardJavaFileManager std = compiler.getStandardFileManager(diags, Locale.ROOT,
        StandardCharsets.UTF_8);
    InMemoryFileManager mgr = new InMemoryFileManager(std);

    List<JavaFileObject> units = new ArrayList<>();
    for (Map.Entry<String, String> e : sources.entrySet()) {
      units.add(new StringSource(e.getKey(), e.getValue()));
    }

    // Put the btrace-core classes on the compile classpath so @ExternalType resolves.
    String cp = System.getProperty("java.class.path");
    List<String> options = Arrays.asList(
        "-classpath", cp,
        "-processor", ExternalTypeProcessor.class.getName());

    JavaCompiler.CompilationTask task =
        compiler.getTask(null, mgr, diags, options, null, units);
    boolean ok = task.call();
    return new Result(ok, mgr.generatedSources(), diags.getDiagnostics());
  }

  private static final class StringSource extends SimpleJavaFileObject {
    private final String src;
    StringSource(String fqn, String src) {
      super(URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.src = src;
    }
    @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return src; }
  }

  private static final class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, ByteArrayOutputStream> bytes = new LinkedHashMap<>();
    private final Map<String, String> sources = new LinkedHashMap<>();
    InMemoryFileManager(JavaFileManager m) { super(m); }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
        JavaFileObject.Kind kind, FileObject sibling) {
      if (kind == JavaFileObject.Kind.SOURCE) {
        return new SimpleJavaFileObject(
            URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
          @Override public Writer openWriter() {
            StringWriter sw = new StringWriter();
            return new Writer() {
              @Override public void write(char[] cbuf, int off, int len) { sw.write(cbuf, off, len); }
              @Override public void flush() {}
              @Override public void close() { sources.put(className, sw.toString()); }
            };
          }
        };
      }
      ByteArrayOutputStream baos = bytes.computeIfAbsent(className, k -> new ByteArrayOutputStream());
      return new SimpleJavaFileObject(
          URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
        @Override public OutputStream openOutputStream() { return baos; }
      };
    }

    Map<String, String> generatedSources() { return sources; }
  }

  private CompileTestHarness() {}
}
```

- [ ] **Step 2: Verify harness compiles**

Run: `./gradlew :btrace-extension-processor:compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add btrace-extension-processor/src/test/java/
git commit -m "test(ext-processor): add in-memory compile test harness"
```

---

## Task 4: Detect annotated interfaces

**Files:**
- Create: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`
- Modify: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`

- [ ] **Step 1: Write the failing test**

Create `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`:

```java
package org.openjdk.btrace.extension.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExternalTypeProcessorTest {

  @Test
  void generatesAdapterForAnnotatedInterface() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.JobStart", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.app.Real\")\n"
        + "public interface JobStart {\n"
        + "  int jobId();\n"
        + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(r.success, "compile failed:\n" + r.errors());
    assertTrue(r.generatedSources.containsKey("com.example.JobStart$Ext"),
        "expected adapter com.example.JobStart$Ext; generated: " + r.generatedSources.keySet());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest`
Expected: FAIL — no adapter generated.

- [ ] **Step 3: Extend processor to emit a stub adapter**

Edit `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`:

```java
package org.openjdk.btrace.extension.processor;

import org.openjdk.btrace.core.extensions.ExternalType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Set;

@SupportedAnnotationTypes("org.openjdk.btrace.core.extensions.ExternalType")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class ExternalTypeProcessor extends AbstractProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) continue;
      TypeElement iface = (TypeElement) e;
      String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
      String simple = iface.getSimpleName().toString();
      String adapterFqn = (pkg.isEmpty() ? "" : pkg + ".") + simple + "$Ext";
      try {
        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(adapterFqn, iface);
        try (PrintWriter w = new PrintWriter(jfo.openWriter())) {
          if (!pkg.isEmpty()) w.println("package " + pkg + ";");
          w.println();
          w.println("public final class " + simple + "$Ext {");
          w.println("  private " + simple + "$Ext() {}");
          w.println("}");
        }
      } catch (Exception ex) {
        processingEnv.getMessager().printMessage(
            javax.tools.Diagnostic.Kind.ERROR,
            "Failed to emit adapter for " + iface.getQualifiedName() + ": " + ex, iface);
      }
    }
    return true;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add btrace-extension-processor/src/
git commit -m "feat(ext-processor): detect @ExternalType interfaces and emit stub adapter"
```

---

## Task 5: Extract method specs

**Files:**
- Create: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/MethodSpec.java`
- Create: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterSpec.java`
- Modify: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`
- Modify: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`

- [ ] **Step 1: Write the failing test**

Append to `ExternalTypeProcessorTest`:

```java
  @Test
  void generatedAdapterContainsDispatchersForEachMethod() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.JobStart", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.app.Real\")\n"
        + "public interface JobStart {\n"
        + "  int jobId();\n"
        + "  long time();\n"
        + "  @ExternalType.Static\n"
        + "  Object create(String name);\n"
        + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(r.success, r.errors());
    String adapter = r.generatedSources.get("com.example.JobStart$Ext");
    assertNotNull(adapter);
    assertTrue(adapter.contains("public static int jobId("), adapter);
    assertTrue(adapter.contains("public static long time("), adapter);
    assertTrue(adapter.contains("public static java.lang.Object create("), adapter);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.generatedAdapterContainsDispatchersForEachMethod`
Expected: FAIL — dispatchers not emitted yet.

- [ ] **Step 3: Create `MethodSpec`**

Create `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/MethodSpec.java`:

```java
package org.openjdk.btrace.extension.processor;

import java.util.List;

final class MethodSpec {
  final String name;
  final String returnType;            // already formatted: "int", "long", "java.lang.String", ...
  final List<String> paramTypes;      // same formatting
  final boolean isStatic;

  MethodSpec(String name, String returnType, List<String> paramTypes, boolean isStatic) {
    this.name = name;
    this.returnType = returnType;
    this.paramTypes = List.copyOf(paramTypes);
    this.isStatic = isStatic;
  }
}
```

- [ ] **Step 4: Create `AdapterSpec`**

Create `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterSpec.java`:

```java
package org.openjdk.btrace.extension.processor;

import java.util.List;

final class AdapterSpec {
  final String pkg;
  final String interfaceSimpleName;   // e.g. "JobStart"
  final String externalFqn;           // e.g. "com.example.app.Real"
  final List<MethodSpec> methods;

  AdapterSpec(String pkg, String interfaceSimpleName, String externalFqn, List<MethodSpec> methods) {
    this.pkg = pkg;
    this.interfaceSimpleName = interfaceSimpleName;
    this.externalFqn = externalFqn;
    this.methods = List.copyOf(methods);
  }

  String adapterSimpleName() { return interfaceSimpleName + "$Ext"; }
  String adapterFqn() { return pkg.isEmpty() ? adapterSimpleName() : pkg + "." + adapterSimpleName(); }
}
```

- [ ] **Step 5: Extract `AdapterSpec` from `TypeElement` in processor**

Replace the body of `ExternalTypeProcessor.process` with:

```java
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) continue;
      TypeElement iface = (TypeElement) e;
      AdapterSpec spec = buildSpec(iface);
      try {
        emit(spec, iface);
      } catch (Exception ex) {
        processingEnv.getMessager().printMessage(
            javax.tools.Diagnostic.Kind.ERROR,
            "Failed to emit adapter for " + iface.getQualifiedName() + ": " + ex, iface);
      }
    }
    return true;
  }

  private AdapterSpec buildSpec(TypeElement iface) {
    String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String simple = iface.getSimpleName().toString();
    String externalFqn = iface.getAnnotation(ExternalType.class).value();
    java.util.List<MethodSpec> methods = new java.util.ArrayList<>();
    for (Element m : iface.getEnclosedElements()) {
      if (m.getKind() != ElementKind.METHOD) continue;
      ExecutableElement em = (ExecutableElement) m;
      if (em.isDefault() || em.getModifiers().contains(Modifier.STATIC)) continue;
      boolean isStatic = em.getAnnotation(ExternalType.Static.class) != null;
      String rt = em.getReturnType().toString();
      java.util.List<String> params = new java.util.ArrayList<>();
      for (VariableElement p : em.getParameters()) params.add(p.asType().toString());
      methods.add(new MethodSpec(em.getSimpleName().toString(), rt, params, isStatic));
    }
    return new AdapterSpec(pkg, simple, externalFqn, methods);
  }

  private void emit(AdapterSpec spec, TypeElement origin) throws java.io.IOException {
    JavaFileObject jfo = processingEnv.getFiler().createSourceFile(spec.adapterFqn(), origin);
    try (PrintWriter w = new PrintWriter(jfo.openWriter())) {
      new AdapterEmitter(spec).render(w);
    }
  }
```

And add imports at the top: `import javax.lang.model.element.Modifier;` and `import javax.lang.model.element.VariableElement;` and `import javax.lang.model.element.ExecutableElement;` (keep all existing imports).

- [ ] **Step 6: Stub `AdapterEmitter` so build compiles**

Create `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterEmitter.java`:

```java
package org.openjdk.btrace.extension.processor;

import java.io.PrintWriter;

final class AdapterEmitter {
  private final AdapterSpec spec;

  AdapterEmitter(AdapterSpec spec) { this.spec = spec; }

  void render(PrintWriter w) {
    if (!spec.pkg.isEmpty()) w.println("package " + spec.pkg + ";");
    w.println();
    w.println("public final class " + spec.adapterSimpleName() + " {");
    w.println("  private " + spec.adapterSimpleName() + "() {}");
    for (MethodSpec m : spec.methods) {
      renderMethod(w, m);
    }
    w.println("}");
  }

  private void renderMethod(PrintWriter w, MethodSpec m) {
    StringBuilder params = new StringBuilder();
    if (!m.isStatic) params.append("java.lang.Object self");
    for (int i = 0; i < m.paramTypes.size(); i++) {
      if (params.length() > 0) params.append(", ");
      params.append(m.paramTypes.get(i)).append(" p").append(i);
    }
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + params + ") {");
    w.println("    throw new UnsupportedOperationException(\"not implemented\");");
    w.println("  }");
  }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add btrace-extension-processor/
git commit -m "feat(ext-processor): extract AdapterSpec + MethodSpec and emit method stubs"
```

---

## Task 6: Lazy-resolve dispatch for virtual methods

**Files:**
- Modify: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/AdapterEmitter.java`
- Modify: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`

- [ ] **Step 1: Write the failing test**

Append to `ExternalTypeProcessorTest`:

```java
  @Test
  void virtualDispatcherUsesLazyMethodHandle() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.JobStart", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.app.Real\")\n"
        + "public interface JobStart {\n"
        + "  int jobId();\n"
        + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(r.success, r.errors());
    String adapter = r.generatedSources.get("com.example.JobStart$Ext");
    assertTrue(adapter.contains("private static volatile java.lang.invoke.MethodHandle"), adapter);
    assertTrue(adapter.contains("findVirtual"), adapter);
    assertTrue(adapter.contains("self.getClass().getClassLoader()"), adapter);
    assertTrue(adapter.contains("(int)"), adapter);  // unboxed return cast
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.virtualDispatcherUsesLazyMethodHandle`
Expected: FAIL — body still throws `UnsupportedOperationException`.

- [ ] **Step 3: Emit real dispatcher body**

Replace `AdapterEmitter` entirely with:

```java
package org.openjdk.btrace.extension.processor;

import java.io.PrintWriter;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class AdapterEmitter {
  private final AdapterSpec spec;

  AdapterEmitter(AdapterSpec spec) { this.spec = spec; }

  void render(PrintWriter w) {
    if (!spec.pkg.isEmpty()) w.println("package " + spec.pkg + ";");
    w.println();
    w.println("import java.lang.invoke.MethodHandle;");
    w.println("import java.lang.invoke.MethodHandles;");
    w.println("import java.lang.invoke.MethodType;");
    w.println();
    w.println("public final class " + spec.adapterSimpleName() + " {");
    w.println("  private static final String OWNER = \"" + spec.externalFqn + "\";");
    w.println("  private " + spec.adapterSimpleName() + "() {}");
    for (MethodSpec m : spec.methods) renderMethod(w, m);
    renderSneak(w);
    w.println("}");
  }

  private void renderMethod(PrintWriter w, MethodSpec m) {
    String mhField = "$" + m.name + "$mh";
    String paramList = paramList(m);
    String argList = argList(m);
    w.println();
    w.println("  private static volatile MethodHandle " + mhField + ";");
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + paramList + ") {");
    w.println("    try {");
    w.println("      MethodHandle h = " + mhField + ";");
    w.println("      if (h == null) h = " + resolverCall(m, mhField) + ";");
    w.print("      ");
    if (!"void".equals(m.returnType)) w.print("return (" + m.returnType + ") ");
    w.println("h.invoke(" + argList + ");");
    w.println("    } catch (Throwable t) { throw sneak(t); }");
    w.println("  }");
    renderResolver(w, m, mhField);
  }

  private String resolverCall(MethodSpec m, String field) {
    String loaderExpr = m.isStatic
        ? "Thread.currentThread().getContextClassLoader()"
        : "self.getClass().getClassLoader()";
    return "$" + m.name + "$resolve(" + loaderExpr + ")";
  }

  private void renderResolver(PrintWriter w, MethodSpec m, String field) {
    w.println();
    w.println("  private static MethodHandle $" + m.name + "$resolve(ClassLoader cl) throws Exception {");
    w.println("    MethodHandle local = " + field + ";");
    w.println("    if (local == null) {");
    w.println("      Class<?> c = Class.forName(OWNER, false, cl);");
    w.print  ("      MethodType mt = MethodType.methodType(");
    w.print(box(m.returnType) + ".class");
    for (String p : m.paramTypes) w.print(", " + box(p) + ".class");
    w.println(");");
    if (m.isStatic) {
      w.println("      local = MethodHandles.publicLookup().findStatic(c, \"" + m.name + "\", mt);");
    } else {
      w.println("      local = MethodHandles.publicLookup().findVirtual(c, \"" + m.name + "\", mt);");
    }
    w.println("      " + field + " = local;");
    w.println("    }");
    w.println("    return local;");
    w.println("  }");
  }

  private void renderSneak(PrintWriter w) {
    w.println();
    w.println("  @SuppressWarnings(\"unchecked\")");
    w.println("  private static <T extends Throwable> RuntimeException sneak(Throwable t) throws T {");
    w.println("    throw (T) t;");
    w.println("  }");
  }

  private String paramList(MethodSpec m) {
    StringBuilder sb = new StringBuilder();
    if (!m.isStatic) sb.append("java.lang.Object self");
    for (int i = 0; i < m.paramTypes.size(); i++) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(m.paramTypes.get(i)).append(" p").append(i);
    }
    return sb.toString();
  }

  private String argList(MethodSpec m) {
    StringBuilder sb = new StringBuilder();
    if (!m.isStatic) sb.append("self");
    for (int i = 0; i < m.paramTypes.size(); i++) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("p").append(i);
    }
    return sb.toString();
  }

  /**
   * MethodType.methodType() only accepts reference types for its varargs tail, but its return type
   * accepts primitives. So we pass primitives as {@code int.class} literals (Java handles this) and
   * reference types via their own class literal. No actual boxing is emitted.
   */
  private String box(String type) {
    return type; // Java accepts "int.class", "void.class", etc. directly.
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.virtualDispatcherUsesLazyMethodHandle`
Expected: PASS. Also confirm the full test class still passes: `./gradlew :btrace-extension-processor:test`.

- [ ] **Step 5: Commit**

```bash
git add btrace-extension-processor/
git commit -m "feat(ext-processor): emit lazy MethodHandle dispatchers for virtual methods"
```

---

## Task 7: Static dispatch via TCCL

**Files:**
- Modify: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`

The emitter already branches on `m.isStatic`. This task verifies it actually works for static methods and that the adapter compiles cleanly when mixed with virtual methods.

- [ ] **Step 1: Write the failing test**

Append:

```java
  @Test
  void staticDispatcherUsesTccl() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.SparkUtils", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.app.SparkUtils\")\n"
        + "public interface SparkUtils {\n"
        + "  @ExternalType.Static\n"
        + "  java.lang.String version();\n"
        + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(r.success, r.errors());
    String adapter = r.generatedSources.get("com.example.SparkUtils$Ext");
    assertTrue(adapter.contains("findStatic"), adapter);
    assertTrue(adapter.contains("Thread.currentThread().getContextClassLoader()"), adapter);
    assertFalse(adapter.contains("java.lang.Object self"), "static dispatcher must not take receiver");
  }
```

- [ ] **Step 2: Run test**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.staticDispatcherUsesTccl`
Expected: PASS (emitter already handles this via `m.isStatic` branch).

- [ ] **Step 3: Commit**

```bash
git add btrace-extension-processor/src/test/
git commit -m "test(ext-processor): verify static dispatcher uses findStatic + TCCL"
```

---

## Task 8: Runtime smoke test — compile + load + invoke

**Files:**
- Modify: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`

Go beyond source inspection and actually load the generated adapter, call it against a real class, and verify it works.

- [ ] **Step 1: Write the failing test**

Append to `ExternalTypeProcessorTest` (add imports at top: `import java.net.URL;`, `import java.net.URLClassLoader;`, `import java.io.File;`, `import java.nio.file.*;`, `import javax.tools.*;`):

```java
  @Test
  void generatedAdapterInvokesRealMethod() throws Exception {
    // The "external" class is just a regular class on the test classpath.
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.target.Counter", ""
        + "package com.example.target;\n"
        + "public class Counter {\n"
        + "  private final int v;\n"
        + "  public Counter(int v) { this.v = v; }\n"
        + "  public int value() { return v; }\n"
        + "}\n");
    sources.put("com.example.adapter.CounterApi", ""
        + "package com.example.adapter;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.target.Counter\")\n"
        + "public interface CounterApi {\n"
        + "  int value();\n"
        + "}\n");

    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    Class<?> counter = r.loader.loadClass("com.example.target.Counter");
    Object instance = counter.getConstructor(int.class).newInstance(42);

    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method m = adapter.getMethod("value", Object.class);
    int got = (int) m.invoke(null, instance);
    assertEquals(42, got);
  }
```

- [ ] **Step 2: Extend `CompileTestHarness` to return a loadable ClassLoader**

Append to `CompileTestHarness.java`:

```java
  public static final class RunnableResult {
    public final boolean success;
    public final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    public final ClassLoader loader;

    RunnableResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diags, ClassLoader loader) {
      this.success = success;
      this.diagnostics = diags;
      this.loader = loader;
    }

    public String errors() {
      return diagnostics.stream()
          .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
          .map(Object::toString)
          .collect(Collectors.joining("\n"));
    }
  }

  public static RunnableResult compileAndLoad(Map<String, String> sources) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    StandardJavaFileManager std = compiler.getStandardFileManager(diags, Locale.ROOT,
        StandardCharsets.UTF_8);
    InMemoryFileManager mgr = new InMemoryFileManager(std);

    List<JavaFileObject> units = new ArrayList<>();
    for (Map.Entry<String, String> e : sources.entrySet()) {
      units.add(new StringSource(e.getKey(), e.getValue()));
    }
    List<String> options = Arrays.asList(
        "-classpath", System.getProperty("java.class.path"),
        "-processor", ExternalTypeProcessor.class.getName());

    JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, diags, options, null, units);
    boolean ok = task.call();
    if (!ok) return new RunnableResult(false, diags.getDiagnostics(), null);

    ClassLoader loader = new ClassLoader(CompileTestHarness.class.getClassLoader()) {
      @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        ByteArrayOutputStream baos = mgr.bytes.get(name);
        if (baos == null) throw new ClassNotFoundException(name);
        byte[] b = baos.toByteArray();
        return defineClass(name, b, 0, b.length);
      }
    };
    return new RunnableResult(true, diags.getDiagnostics(), loader);
  }
```

Also promote `bytes` from `private` to package-private in `InMemoryFileManager`.

- [ ] **Step 3: Run test**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.generatedAdapterInvokesRealMethod`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add btrace-extension-processor/
git commit -m "test(ext-processor): end-to-end smoke test for generated adapter invocation"
```

---

## Task 9: Validation errors

**Files:**
- Modify: `btrace-extension-processor/src/main/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessor.java`
- Modify: `btrace-extension-processor/src/test/java/org/openjdk/btrace/extension/processor/ExternalTypeProcessorTest.java`

The processor must reject clearly-wrong uses with actionable messages. In scope for v1: non-interface targets and empty `value()`. Out of scope (silently ignored): default methods, static interface methods.

- [ ] **Step 1: Write the failing tests**

Append:

```java
  @Test
  void rejectsAnnotationOnClass() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.NotAnInterface", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"com.example.app.Real\")\n"
        + "public class NotAnInterface {}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success, "expected compile to fail");
    assertTrue(r.errors().contains("@ExternalType can only be applied to interfaces"), r.errors());
  }

  @Test
  void rejectsEmptyValue() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.Empty", ""
        + "package com.example;\n"
        + "import org.openjdk.btrace.core.extensions.ExternalType;\n"
        + "@ExternalType(\"\")\n"
        + "public interface Empty { int x(); }\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success, "expected compile to fail");
    assertTrue(r.errors().contains("@ExternalType.value() must be a non-empty class name"), r.errors());
  }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :btrace-extension-processor:test --tests ExternalTypeProcessorTest.rejectsAnnotationOnClass --tests ExternalTypeProcessorTest.rejectsEmptyValue`
Expected: FAIL.

- [ ] **Step 3: Add validation to processor**

In `ExternalTypeProcessor.process`, before the `AdapterSpec spec = buildSpec(iface);` line, insert:

```java
      if (e.getKind() != ElementKind.INTERFACE) {
        processingEnv.getMessager().printMessage(
            javax.tools.Diagnostic.Kind.ERROR,
            "@ExternalType can only be applied to interfaces; found " + e.getKind() + " " + e, e);
        continue;
      }
      TypeElement iface0 = (TypeElement) e;
      String val = iface0.getAnnotation(ExternalType.class).value();
      if (val == null || val.isEmpty()) {
        processingEnv.getMessager().printMessage(
            javax.tools.Diagnostic.Kind.ERROR,
            "@ExternalType.value() must be a non-empty class name on " + iface0.getQualifiedName(),
            iface0);
        continue;
      }
```

And remove the duplicate `if (e.getKind() != ElementKind.INTERFACE) continue;` check above. The `TypeElement iface = (TypeElement) e;` cast becomes redundant; rename the surviving cast to `iface` and use it.

- [ ] **Step 4: Run tests**

Run: `./gradlew :btrace-extension-processor:test`
Expected: PASS (all tests including the earlier ones).

- [ ] **Step 5: Commit**

```bash
git add btrace-extension-processor/
git commit -m "feat(ext-processor): emit compile errors for invalid @ExternalType targets"
```

---

## Task 10: Wire processor into `BTraceExtensionPlugin`

**Files:**
- Modify: `btrace-gradle-plugin/src/main/groovy/org/openjdk/btrace/gradle/BTraceExtensionPlugin.groovy`

- [ ] **Step 1: Locate the api source set configuration**

Find the block where `sourceSets.api` is configured (search for `sourceSets.api` in the file). The processor must be added as `apiAnnotationProcessor`.

- [ ] **Step 2: Register processor dependency**

Inside `project.afterEvaluate` (or the existing dependency-wiring block — read the file to locate the right spot), add:

```groovy
project.dependencies.add('apiAnnotationProcessor',
    "org.openjdk.btrace:btrace-extension-processor:${project.version}")
```

If the plugin already uses `btraceVersion` or similar, follow that convention.

- [ ] **Step 3: Publish processor to local maven for plugin tests**

```bash
./gradlew :btrace-extension-processor:publishToMavenLocal
```

- [ ] **Step 4: Build an extension example that uses `@ExternalType`**

Modify `btrace-extensions/examples/btrace-spark/src/api/java/org/example/btrace/spark/api/SparkApi.java` (or add a new interface alongside it) to use `@ExternalType` for one method as a smoke test. Commit this as part of the next task; for now just verify:

```bash
./gradlew :btrace-extensions:examples:btrace-spark:buildApiJar
```

Expected: BUILD SUCCESSFUL — no changes needed to existing example if plugin wiring is correct (processor simply finds nothing to do).

- [ ] **Step 5: Commit**

```bash
git add btrace-gradle-plugin/
git commit -m "feat(ext-plugin): auto-register @ExternalType annotation processor on api source set"
```

---

## Task 11: Integration test in a real extension

**Files:**
- Create: `integration-tests/src/test/java/org/openjdk/btrace/itests/ExternalTypeAdapterIT.java`
- Create: `integration-tests/src/test/btrace/ExternalTypeAdapterProbe.java`

Use an existing integration-test target app (pick one from `integration-tests/src/test/resources/`) and verify that a BTrace probe with an `@ExternalType`-based adapter works end-to-end.

- [ ] **Step 1: Inspect existing integration-test conventions**

Run: `ls integration-tests/src/test/java/org/openjdk/btrace/itests/ | head -20`
Read one existing IT to understand the fixture pattern. Choose one that uses a simple target app with method invocations.

- [ ] **Step 2: Design the probe**

The probe declares an `@ExternalType`-annotated interface pointing at a target-app class (e.g., the test fixture's counter class). It calls the generated `$Ext` adapter inside an `@OnMethod` handler.

- [ ] **Step 3: Write the probe source**

Create `integration-tests/src/test/btrace/ExternalTypeAdapterProbe.java`. Use the same BTrace-probe conventions as neighboring probes in that directory. Invoke the generated adapter against a method of the target class. Emit output via `BTraceUtils.println`.

- [ ] **Step 4: Write the IT driver**

Create `integration-tests/src/test/java/org/openjdk/btrace/itests/ExternalTypeAdapterIT.java`. Follow the same pattern as an existing IT — launch target, attach agent with the probe, assert the expected println text appeared.

- [ ] **Step 5: Run the IT**

Run: `./gradlew :btrace-dist:build && ./gradlew :integration-tests:test --tests ExternalTypeAdapterIT -Pintegration`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add integration-tests/
git commit -m "test(itests): end-to-end integration test for @ExternalType adapter"
```

---

## Task 12: Documentation

**Files:**
- Modify: `docs/architecture/provided-style-extensions.md`

- [ ] **Step 1: Add "External Type Adapters" section**

After the existing "Impl Sketch" section, insert a new subsection titled `## External Type Adapters (Recommended)` that:

1. States the motivation in one paragraph (no boilerplate, no string method names, refactor-safe, lazy).
2. Shows a before/after comparison: hand-written `MethodHandleCache` adapter vs `@ExternalType` interface + `$Ext` call.
3. Lists the rules (TYPE target, interface only, non-empty `value`, `@Static` for statics, unresolvable types → `Object`).
4. Lists v1 scope limits (no field access, no constructors, no `instanceof`) and points to `ClassLoadingUtil` for those cases.

The example must match the Spark example's style. Keep it under 80 lines.

- [ ] **Step 2: Update `btrace-gradle-plugin/README.md`**

Add a short `### @ExternalType Adapter Generator` subsection under `## BTrace Extension Plugin → Features` explaining that the processor is auto-registered.

- [ ] **Step 3: Commit**

```bash
git add docs/architecture/provided-style-extensions.md btrace-gradle-plugin/README.md
git commit -m "docs: document @ExternalType adapter generator"
```

---

## Task 13: Branch self-review + PR

- [ ] **Step 1: Rebuild from scratch**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run spotlessCheck**

```bash
./gradlew spotlessCheck
```

If it fails, run `./gradlew spotlessApply` and commit formatting as a final cleanup commit.

- [ ] **Step 3: Inspect the branch diff**

```bash
git log --oneline jb/configurations..HEAD
git diff jb/configurations...HEAD --stat
```

Confirm no unintended files, no stray commits, no TODOs left in production source.

- [ ] **Step 4: Push and open PR**

```bash
git push -u origin jb/external-type-adapter
gh pr create --base develop --title "feat: @ExternalType adapter generator for provided-style extensions" --body "$(cat <<'EOF'
## Rationale

Provided-style extension impls currently reach into application types via hand-written `MethodHandleCache` adapters. That works but has three ergonomic costs:

- **String method names.** `mh.findVirtual(cls, "jobId", int.class)` is not refactor-safe and IDE tooling can't check it.
- **Eager-resolution foot-gun.** `static final MethodHandle` fields fail extension init if the target class isn't yet visible.
- **Boilerplate per call site.** Every reflective access expands into 5+ lines of try/catch, type wiring, and cache plumbing.

## Solution

`@ExternalType("com.example.app.Real")` on an interface + build-time annotation processor → generated `$Ext` adapter with typed static dispatchers, lazy `volatile MethodHandle` resolution, and no per-call cache lookup once warm.

## Quick Start

```java
@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface JobStart {
  int jobId();
  long time();
}
```

Call site:

```java
int id = JobStart$Ext.jobId(evt);
```

## Scope

**In:** virtual + static method adapters, primitive/reference return and parameter types, lazy resolution via receiver defining loader (virtual) or TCCL (static), compile-time validation.

**Out (v1):** field access, constructors, `instanceof` / `checkcast`, generic container erasure. Use `ClassLoadingUtil` for those.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Verify PR**

```bash
gh pr view --web
```

Eyeball the PR description and confirm all commits are present.

---

## Self-Review Notes

- **Spec coverage:** annotation ✓ (Task 1), module scaffolding ✓ (Task 2), compile harness ✓ (Task 3), detection ✓ (Task 4), spec extraction ✓ (Task 5), virtual dispatch ✓ (Task 6), static dispatch ✓ (Task 7), end-to-end invocation ✓ (Task 8), validation ✓ (Task 9), plugin wiring ✓ (Task 10), IT ✓ (Task 11), docs ✓ (Task 12), PR ✓ (Task 13).
- **Type consistency:** `AdapterSpec` / `MethodSpec` / `AdapterEmitter` names used consistently; `$Ext` suffix used consistently; `$<name>$mh` field naming consistent.
- **Out-of-scope items explicitly flagged:** field access, constructors, `instanceof`. Mentioned in Task 9 scope note, Task 12 docs, and the PR description.
- **Known follow-ups not included in this plan:** support for `@ExternalType` interfaces referencing other `@ExternalType` interfaces (chained adapters), JMH microbenchmark comparing lazy `volatile` vs. `ClassValue`-based caching, IDE integration (IntelliJ annotation processor hint). Park these for v2.
