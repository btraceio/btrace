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
    assertTrue(adapter.contains("(int)"), adapter);
  }

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
    assertFalse(adapter.contains("java.lang.Object self"),
        "static dispatcher must not take a receiver parameter: " + adapter);
  }

  @Test
  void generatedAdapterInvokesRealMethod() throws Exception {
    // The "external" class is just a regular class in the compile unit.
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
    assertTrue(r.errors().contains("@ExternalType can only be applied to interfaces"),
        r.errors());
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
    assertTrue(r.errors().contains("@ExternalType.value() must be a non-empty class name"),
        r.errors());
  }
}
