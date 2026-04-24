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
