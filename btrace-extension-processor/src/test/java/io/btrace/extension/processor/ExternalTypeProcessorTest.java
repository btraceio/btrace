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

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalTypeProcessorTest {

  @Test
  void generatesAdapterForAnnotatedInterface() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.JobStart",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"com.example.app.Real\")\n"
            + "public interface JobStart {\n"
            + "  int jobId();\n"
            + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(r.success, "compile failed:\n" + r.errors());
    assertTrue(
        r.generatedSources.containsKey("com.example.JobStart$Ext"),
        "expected adapter com.example.JobStart$Ext; generated: " + r.generatedSources.keySet());
  }

  @Test
  void generatedAdapterContainsDispatchersForEachMethod() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.JobStart",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
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
    sources.put(
        "com.example.JobStart",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
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
    sources.put(
        "com.example.SparkUtils",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
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
    assertFalse(
        adapter.contains("java.lang.Object self"),
        "static dispatcher must not take a receiver parameter: " + adapter);
  }

  @Test
  void generatedAdapterInvokesRealMethod() throws Exception {
    // The "external" class is just a regular class in the compile unit.
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Counter",
        ""
            + "package com.example.target;\n"
            + "public class Counter {\n"
            + "  private final int v;\n"
            + "  public Counter(int v) { this.v = v; }\n"
            + "  public int value() { return v; }\n"
            + "}\n");
    sources.put(
        "com.example.adapter.CounterApi",
        ""
            + "package com.example.adapter;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
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
  void staticDispatcherResolvesViaContextClassLoader() throws Exception {
    // The "external" class lives only in the in-memory loader; static dispatch must
    // use the TCCL (set here) to find it at runtime.
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Greeter",
        ""
            + "package com.example.target;\n"
            + "public class Greeter {\n"
            + "  public static String hello() { return \"hello\"; }\n"
            + "}\n");
    sources.put(
        "com.example.adapter.GreeterApi",
        ""
            + "package com.example.adapter;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"com.example.target.Greeter\")\n"
            + "public interface GreeterApi {\n"
            + "  @ExternalType.Static\n"
            + "  java.lang.String hello();\n"
            + "}\n");

    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(r.loader);
      Class<?> adapter = r.loader.loadClass("com.example.adapter.GreeterApi$Ext");
      java.lang.reflect.Method m = adapter.getMethod("hello");
      assertEquals("hello", m.invoke(null));
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
  }

  @Test
  void staticDispatcherFallsBackToSystemLoaderWhenTcclNull() throws Exception {
    // Simulates a bootstrap/JVM-internal thread where TCCL is null.
    // The adapter must fall back to ClassLoader.getSystemClassLoader() instead of throwing NPE.
    // Uses java.lang.System, which is always on the boot/system classloader.
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.SysApi",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"java.lang.System\")\n"
            + "public interface SysApi {\n"
            + "  @ExternalType.Static\n"
            + "  long currentTimeMillis();\n"
            + "}\n");

    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(null);
      Class<?> adapter = r.loader.loadClass("com.example.SysApi$Ext");
      java.lang.reflect.Method m = adapter.getMethod("currentTimeMillis");
      long ts = (long) m.invoke(null);
      assertTrue(ts > 0, "expected valid timestamp via system-CL fallback; got " + ts);
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
  }

  @Test
  void rejectsAnnotationOnClass() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.NotAnInterface",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"com.example.app.Real\")\n"
            + "public class NotAnInterface {}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success, "expected compile to fail");
    assertTrue(r.errors().contains("@ExternalType can only be applied to interfaces"), r.errors());
  }

  @Test
  void rejectsEmptyValue() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.Empty",
        ""
            + "package com.example;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"\")\n"
            + "public interface Empty { int x(); }\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success, "expected compile to fail");
    assertTrue(
        r.errors().contains("@ExternalType.value() must be a non-empty class name"), r.errors());
  }

  @Test
  void generatedAdapterHandlesParameterizedTypes() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.Listy",
        ""
            + "package com.example;\n"
            + "import java.util.List;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"com.example.app.Real\")\n"
            + "public interface Listy {\n"
            + "  java.util.List<java.lang.String> items();\n"
            + "  void process(java.util.List<java.lang.String> items);\n"
            + "}\n");

    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertTrue(
        r.success,
        "compile failed; generated source likely has a bad type literal. errors:\n" + r.errors());
    String adapter = r.generatedSources.get("com.example.Listy$Ext");
    assertNotNull(adapter);
    // Raw type must appear in the MethodType literal (no angle brackets).
    assertTrue(adapter.contains("MethodType.methodType(java.util.List.class"), adapter);
    assertFalse(adapter.contains("java.util.List<java.lang.String>.class"), adapter);
  }
}
