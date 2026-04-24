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
}
