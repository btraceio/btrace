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

import io.btrace.core.extensions.ExternalTypeResolutionException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    assertTrue(adapter.contains("ClassValue<ResolvedCall>"), adapter);
    assertTrue(adapter.contains("findVirtual"), adapter);
    assertTrue(adapter.contains("receiver.getClassLoader()"), adapter);
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
    assertTrue(adapter.contains("public static java.lang.String version()"), adapter);
    assertTrue(
        adapter.contains("public static java.lang.String version(ClassLoader applicationLoader)"),
        adapter);
    assertTrue(adapter.contains("MethodType.methodType(java.lang.String.class)"), adapter);
    assertTrue(adapter.contains("call.handle.invoke()"), adapter);
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
  void explicitStaticDispatcherSelectsLoaderWithoutChangingTccl() throws Exception {
    Map<String, String> sources =
        externalTypeSources("@ExternalType.Static int value();", "return 7;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    MutableTargetLoader targetLoader = new MutableTargetLoader(r, true);
    MutableTargetLoader wrongLoader = new MutableTargetLoader(r, false);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method legacy = adapter.getMethod("value");
    java.lang.reflect.Method explicit = adapter.getMethod("value", ClassLoader.class);
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(wrongLoader);
      ExternalTypeResolutionException failure =
          assertResolutionFailure(legacy, null, "com.example.target.Counter", "value");
      assertInstanceOf(ClassNotFoundException.class, failure.getCause());
      assertEquals(7, explicit.invoke(null, targetLoader));
      assertSame(wrongLoader, Thread.currentThread().getContextClassLoader());
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
  }

  @Test
  void explicitStaticDispatcherRejectsNullBeforeResolution() throws Exception {
    Map<String, String> sources =
        externalTypeSources("@ExternalType.Static int value();", "return 7;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method explicit = adapter.getMethod("value", ClassLoader.class);

    InvocationTargetException failure =
        assertThrows(
            InvocationTargetException.class, () -> explicit.invoke(null, new Object[] {null}));
    assertInstanceOf(NullPointerException.class, failure.getCause());
    assertEquals("applicationLoader", failure.getCause().getMessage());
    assertEquals(0, resolutionAttempts(adapter, 0));
    assertEquals(0, staticLoaderIndexEntries(adapter, 0));
  }

  @Test
  void explicitStaticDispatcherSharesLegacyCacheAndRetriesFailures() throws Exception {
    Map<String, String> sources =
        externalTypeSources("@ExternalType.Static int value();", "return 7;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());
    MutableTargetLoader loader = new MutableTargetLoader(r, false);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method legacy = adapter.getMethod("value");
    java.lang.reflect.Method explicit = adapter.getMethod("value", ClassLoader.class);

    ExternalTypeResolutionException failure =
        assertResolutionFailure(explicit, loader, "com.example.target.Counter", "value");
    assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    loader.makeVisible();
    assertEquals(7, explicit.invoke(null, loader));
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(loader);
      assertEquals(7, legacy.invoke(null));
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
    assertEquals(2, loader.targetLoads, "failure retries and legacy shares the successful entry");
    assertEquals(1, resolutionAttempts(adapter, 0));
  }

  @Test
  void explicitStaticDispatcherForwardsRealClassLoaderArgument() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Installer",
        "package com.example.target; public class Installer { "
            + "public static boolean install(ClassLoader loader) { return loader != null; } }");
    sources.put(
        "com.example.adapter.InstallerApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Installer\") public interface InstallerApi { "
            + "@ExternalType.Static boolean install(ClassLoader targetArgument); }");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());
    Class<?> adapter = r.loader.loadClass("com.example.adapter.InstallerApi$Ext");
    java.lang.reflect.Method legacy = adapter.getMethod("install", ClassLoader.class);
    java.lang.reflect.Method explicit =
        adapter.getMethod("install", ClassLoader.class, ClassLoader.class);
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(r.loader);
      assertEquals(true, legacy.invoke(null, r.loader));
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
    assertEquals(true, explicit.invoke(null, r.loader, r.loader));
  }

  @Test
  void generatedAdaptersCacheHandlesPerApplicationClassLoader() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Counter",
        ""
            + "package com.example.target;\n"
            + "public class Counter {\n"
            + "  private final int value;\n"
            + "  public Counter(int value) { this.value = value; }\n"
            + "  public int value() { return value; }\n"
            + "  public static String loaderId() { return String.valueOf(Counter.class.getClassLoader()); }\n"
            + "}\n");
    sources.put(
        "com.example.adapter.CounterApi",
        ""
            + "package com.example.adapter;\n"
            + "import io.btrace.core.extensions.ExternalType;\n"
            + "@ExternalType(\"com.example.target.Counter\")\n"
            + "public interface CounterApi {\n"
            + "  int value();\n"
            + "  @ExternalType.Static String loaderId();\n"
            + "}\n");

    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    ClassLoader first = isolatedExternalLoader(r);
    ClassLoader second = isolatedExternalLoader(r);
    Class<?> firstCounter = first.loadClass("com.example.target.Counter");
    Class<?> secondCounter = second.loadClass("com.example.target.Counter");
    Object firstInstance = firstCounter.getConstructor(int.class).newInstance(1);
    Object secondInstance = secondCounter.getConstructor(int.class).newInstance(2);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");

    java.lang.reflect.Method value = adapter.getMethod("value", Object.class);
    assertEquals(1, value.invoke(null, firstInstance));
    assertEquals(2, value.invoke(null, secondInstance));

    java.lang.reflect.Method loaderId = adapter.getMethod("loaderId");
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(first);
      String firstId = (String) loaderId.invoke(null);
      Thread.currentThread().setContextClassLoader(second);
      String secondId = (String) loaderId.invoke(null);
      assertNotEquals(firstId, secondId);
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
  }

  private static ClassLoader isolatedExternalLoader(CompileTestHarness.RunnableResult result) {
    return new ClassLoader(result.loader) {
      @Override
      protected synchronized Class<?> loadClass(String name, boolean resolve)
          throws ClassNotFoundException {
        if (!"com.example.target.Counter".equals(name)) return super.loadClass(name, resolve);
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          byte[] bytes =
              java.util.Objects.requireNonNull(
                  result.classBytes.get(name), "Missing class bytes for " + name);
          loaded = defineClass(name, bytes, 0, bytes.length);
        }
        if (resolve) resolveClass(loaded);
        return loaded;
      }
    };
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

  @Test
  void markedObjectSignaturesResolveTargetTypesAndChainWithoutContractDescriptors()
      throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Child",
        "package com.example.target; public class Child { public String label() { return \"child\"; } }");
    sources.put(
        "com.example.target.Parent",
        "package com.example.target; public class Parent { "
            + "public Child child() { return new Child(); } "
            + "public String replace(Child child) { return child.label(); } }");
    sources.put(
        "com.example.adapter.ChildApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Child\") public interface ChildApi { "
            + "String label(); }");
    sources.put(
        "com.example.adapter.ParentApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Parent\") public interface ParentApi { "
            + "@ExternalType.Type(ChildApi.class) Object child(); "
            + "String replace(@ExternalType.Type(ChildApi.class) Object child); }");
    CompileTestHarness.Result compiled = CompileTestHarness.compile(sources);
    assertTrue(compiled.success, compiled.errors());
    String adapterSource = compiled.generatedSources.get("com.example.adapter.ParentApi$Ext");
    assertTrue(adapterSource.contains("Class.forName(\"com.example.target.Child\", false, owner.getClassLoader())"));
    assertFalse(adapterSource.contains("ChildApi.class"), adapterSource);
    assertFalse(adapterSource.contains("com.example.target.Child.class"), adapterSource);

    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());
    Object parent = r.loader.loadClass("com.example.target.Parent").getConstructor().newInstance();
    Class<?> parentAdapter = r.loader.loadClass("com.example.adapter.ParentApi$Ext");
    Object child = parentAdapter.getMethod("child", Object.class).invoke(null, parent);
    Class<?> childAdapter = r.loader.loadClass("com.example.adapter.ChildApi$Ext");
    assertEquals("child", childAdapter.getMethod("label", Object.class).invoke(null, child));
    assertEquals(
        "child", parentAdapter.getMethod("replace", Object.class, Object.class).invoke(null, parent, child));
  }

  @Test
  void rejectsInvalidTargetTypeMarkerWithoutEmittingAdapter() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("com.example.NotAContract", "package com.example; public interface NotAContract {}");
    sources.put(
        "com.example.BadApi",
        "package com.example; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.Target\") public interface BadApi { "
            + "@ExternalType.Type(NotAContract.class) Object child(); }");
    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success);
    assertTrue(r.errors().contains("must reference an interface with a non-empty @ExternalType value"));
    assertFalse(r.generatedSources.containsKey("com.example.BadApi$Ext"));
  }

  @Test
  void rejectsMarkersOutsideAdaptedMethodsAndNonObjectDeclarations() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.ChildApi",
        "package com.example; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"target.Child\") public interface ChildApi { String label(); }");
    sources.put(
        "com.example.Outside",
        "package com.example; import io.btrace.core.extensions.ExternalType; "
            + "public interface Outside { @ExternalType.Type(ChildApi.class) Object child(); }");
    sources.put(
        "com.example.BadApi",
        "package com.example; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"target.Parent\") public interface BadApi { "
            + "@ExternalType.Type(ChildApi.class) String child(); "
            + "default @ExternalType.Type(ChildApi.class) Object skipped() { return null; } }");
    CompileTestHarness.Result r = CompileTestHarness.compile(sources);
    assertFalse(r.success);
    assertTrue(r.errors().contains("only valid as @ExternalType.Type(OtherContract.class) Object"));
    assertTrue(r.errors().contains("non-static, non-default method"));
    assertFalse(r.generatedSources.containsKey("com.example.BadApi$Ext"));
  }

  @Test
  void markedStaticSignatureUsesOwnerDefiningLoaderForLegacyAndExplicitCalls() throws Exception {
    Map<String, String> sources = chainSources(true);
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());
    ChainLoader ownerLoader = new ChainLoader(r, "parent");
    ClassLoader selectedChild =
        new ClassLoader(ownerLoader) {
          @Override
          protected synchronized Class<?> loadClass(String name, boolean resolve)
              throws ClassNotFoundException {
            if (!"com.example.target.Child".equals(name)) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
              byte[] bytes = r.classBytes.get(name);
              loaded = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(loaded);
            return loaded;
          }
        };
    Class<?> adapter = r.loader.loadClass("com.example.adapter.ParentApi$Ext");
    java.lang.reflect.Method legacy = adapter.getMethod("child");
    java.lang.reflect.Method explicit = adapter.getMethod("child", ClassLoader.class);
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(selectedChild);
      Object legacyChild = legacy.invoke(null);
      assertSame(ownerLoader, legacyChild.getClass().getClassLoader());
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
    Object explicitChild = explicit.invoke(null, selectedChild);
    assertSame(ownerLoader, explicitChild.getClass().getClassLoader());
  }

  @Test
  void markedChainsKeepLoaderIdentityAndRetryMissingSignatureType() throws Exception {
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(chainSources(false));
    assertTrue(r.success, r.errors());
    ChainLoader first = new ChainLoader(r, "first");
    ChainLoader second = new ChainLoader(r, "second");
    Class<?> parentAdapter = r.loader.loadClass("com.example.adapter.ParentApi$Ext");
    Class<?> childAdapter = r.loader.loadClass("com.example.adapter.ChildApi$Ext");
    Object firstParent = first.loadClass("com.example.target.Parent").getConstructor().newInstance();
    Object secondParent = second.loadClass("com.example.target.Parent").getConstructor().newInstance();
    Object firstChild = parentAdapter.getMethod("child", Object.class).invoke(null, firstParent);
    Object secondChild = parentAdapter.getMethod("child", Object.class).invoke(null, secondParent);
    assertEquals("first", childAdapter.getMethod("label", Object.class).invoke(null, firstChild));
    assertEquals("second", childAdapter.getMethod("label", Object.class).invoke(null, secondChild));
    InvocationTargetException wrong =
        assertThrows(
            InvocationTargetException.class,
            () ->
                parentAdapter
                    .getMethod("replace", Object.class, Object.class)
                    .invoke(null, firstParent, secondChild));
    assertTrue(
        wrong.getCause() instanceof ClassCastException
            || wrong.getCause() instanceof java.lang.invoke.WrongMethodTypeException);

    ChainLoader missing = new ChainLoader(r, "missing");
    missing.hideChild();
    Object missingParent = missing.loadClass("com.example.target.Parent").getConstructor().newInstance();
    ExternalTypeResolutionException failure =
        assertResolutionFailure(
            parentAdapter.getMethod("child", Object.class), missingParent, "com.example.target.Parent", "child");
    assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    missing.showChild();
    assertNotNull(parentAdapter.getMethod("child", Object.class).invoke(null, missingParent));
  }

  @Test
  void markedNullReturnAndParameterPreserveOpaqueAndVirtualNullSemantics() throws Exception {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Child",
        "package com.example.target; public class Child { public String label() { return \"child\"; } }");
    sources.put(
        "com.example.target.Parent",
        "package com.example.target; public class Parent { "
            + "public Child nullChild() { return null; } "
            + "public String accept(Child child) { return child == null ? \"null-ok\" : \"wrong\"; } }");
    sources.put(
        "com.example.adapter.ChildApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Child\") public interface ChildApi { String label(); }");
    sources.put(
        "com.example.adapter.ParentApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Parent\") public interface ParentApi { "
            + "@ExternalType.Type(ChildApi.class) Object nullChild(); "
            + "String accept(@ExternalType.Type(ChildApi.class) Object child); }");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    Object parent = r.loader.loadClass("com.example.target.Parent").getConstructor().newInstance();
    Class<?> parentAdapter = r.loader.loadClass("com.example.adapter.ParentApi$Ext");
    Class<?> childAdapter = r.loader.loadClass("com.example.adapter.ChildApi$Ext");
    Object child = parentAdapter.getMethod("nullChild", Object.class).invoke(null, parent);
    assertNull(child);
    assertEquals(
        "null-ok", parentAdapter.getMethod("accept", Object.class, Object.class).invoke(null, parent, null));
    InvocationTargetException nullReceiver =
        assertThrows(
            InvocationTargetException.class,
            () -> childAdapter.getMethod("label", Object.class).invoke(null, new Object[] {null}));
    assertInstanceOf(NullPointerException.class, nullReceiver.getCause());
  }

  @Test
  void virtualResolutionRetriesAfterSameLoaderMakesTargetVisibleAndCachesSuccess()
      throws Exception {
    Map<String, String> sources = externalTypeSources("int value();", "return 42;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    MutableTargetLoader loader = new MutableTargetLoader(r, false);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method value = adapter.getMethod("value", Object.class);
    Object context = loader.loadClass("com.example.target.Context").getConstructor().newInstance();
    assertSame(loader, context.getClass().getClassLoader());
    ExternalTypeResolutionException failure =
        assertResolutionFailure(value, context, "com.example.target.Counter", "value");
    assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    assertEquals(1, loader.targetLoads, "same loader must attempt the missing target class");

    loader.makeVisible();
    Object counter = loader.loadClass("com.example.target.Counter").getConstructor().newInstance();
    assertEquals(42, value.invoke(null, counter));
    assertEquals(42, value.invoke(null, counter));
    assertEquals(2, loader.targetLoads, "same loader must retry after making the target visible");
    assertEquals(1, resolutionAttempts(adapter, 0), "successful member resolution must be cached");
  }

  @Test
  void staticResolutionUsesLoaderIdentityAndCachesEachTccl() throws Exception {
    Map<String, String> sources =
        externalTypeSources("@ExternalType.Static int value();", "return 7;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    MutableTargetLoader first = new MutableTargetLoader(r, false);
    MutableTargetLoader second = new MutableTargetLoader(r, true);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method value = adapter.getMethod("value");
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(first);
      assertResolutionFailure(value, null, "com.example.target.Counter", "value");
      first.makeVisible();
      assertEquals(7, value.invoke(null));
      assertEquals(7, value.invoke(null));
      Thread.currentThread().setContextClassLoader(second);
      assertEquals(7, value.invoke(null));
      assertEquals(7, value.invoke(null));
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
    assertEquals(2, first.targetLoads, "failed static resolution must not be cached");
    assertEquals(1, second.targetLoads);
    assertEquals(2, resolutionAttempts(adapter, 0));
  }

  @Test
  void staticFirstUseResolvesOnceUnderTheMemberMonitor() throws Exception {
    Map<String, String> sources =
        externalTypeSources("@ExternalType.Static int value();", "return 7;");
    CompileTestHarness.RunnableResult r = CompileTestHarness.compileAndLoad(sources);
    assertTrue(r.success, r.errors());

    MutableTargetLoader loader = new MutableTargetLoader(r, true);
    Class<?> adapter = r.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method legacy = adapter.getMethod("value");
    java.lang.reflect.Method explicit = adapter.getMethod("value", ClassLoader.class);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> first = executor.submit(() -> invokeStatic(legacy, loader, ready, start));
      Future<Integer> second =
          executor.submit(() -> invokeExplicitStatic(explicit, loader, ready, start));
      ready.await();
      start.countDown();
      assertEquals(7, first.get());
      assertEquals(7, second.get());
    } finally {
      executor.shutdownNow();
    }
    assertEquals(1, loader.targetLoads, "static first use must load once");
    assertEquals(1, resolutionAttempts(adapter, 0), "static first use must resolve once");
  }

  @Test
  void missingMembersRetryAndTargetFailuresStayTransparent() throws Exception {
    Map<String, String> missing = externalTypeSources("int value();", "return 1;", "different");
    CompileTestHarness.RunnableResult missingResult = CompileTestHarness.compileAndLoad(missing);
    assertTrue(missingResult.success, missingResult.errors());
    Class<?> missingAdapter = missingResult.loader.loadClass("com.example.adapter.CounterApi$Ext");
    java.lang.reflect.Method missingValue = missingAdapter.getMethod("value", Object.class);
    MutableTargetLoader missingLoader = new MutableTargetLoader(missingResult, true);
    Object missingTarget =
        missingLoader.loadClass("com.example.target.Counter").getConstructor().newInstance();
    ExternalTypeResolutionException first =
        assertResolutionFailure(missingValue, missingTarget, "com.example.target.Counter", "value");
    ExternalTypeResolutionException second =
        assertResolutionFailure(missingValue, missingTarget, "com.example.target.Counter", "value");
    assertInstanceOf(NoSuchMethodException.class, first.getCause());
    assertInstanceOf(NoSuchMethodException.class, second.getCause());
    assertEquals(2, resolutionAttempts(missingAdapter, 0));

    Map<String, String> throwing =
        externalTypeSources(
            "int value();",
            "throw new io.btrace.core.extensions.ExternalTypeResolutionException(\"target\", \"value\", new IllegalAccessException());");
    CompileTestHarness.RunnableResult throwingResult = CompileTestHarness.compileAndLoad(throwing);
    assertTrue(throwingResult.success, throwingResult.errors());
    MutableTargetLoader throwingLoader = new MutableTargetLoader(throwingResult, true);
    Object throwingTarget =
        throwingLoader.loadClass("com.example.target.Counter").getConstructor().newInstance();
    java.lang.reflect.Method throwingValue =
        throwingResult
            .loader
            .loadClass("com.example.adapter.CounterApi$Ext")
            .getMethod("value", Object.class);
    InvocationTargetException thrown =
        assertThrows(
            InvocationTargetException.class, () -> throwingValue.invoke(null, throwingTarget));
    assertInstanceOf(ExternalTypeResolutionException.class, thrown.getCause());
    assertEquals("Unable to resolve @ExternalType target#value", thrown.getCause().getMessage());
  }

  @Test
  void exactSignatureAndPublicLookupFailuresPreserveTheirCauses() throws Exception {
    Map<String, String> mismatched = externalTypeSources("int value();", "return 42L;");
    CompileTestHarness.RunnableResult mismatchResult =
        CompileTestHarness.compileAndLoad(mismatched);
    assertTrue(mismatchResult.success, mismatchResult.errors());
    MutableTargetLoader mismatchLoader = new MutableTargetLoader(mismatchResult, true);
    Object mismatchTarget =
        mismatchLoader.loadClass("com.example.target.Counter").getConstructor().newInstance();
    java.lang.reflect.Method mismatchValue =
        mismatchResult
            .loader
            .loadClass("com.example.adapter.CounterApi$Ext")
            .getMethod("value", Object.class);
    ExternalTypeResolutionException mismatch =
        assertResolutionFailure(
            mismatchValue, mismatchTarget, "com.example.target.Counter", "value");
    assertInstanceOf(NoSuchMethodException.class, mismatch.getCause());

    Map<String, String> inaccessible = new LinkedHashMap<>();
    inaccessible.put(
        "com.example.target.Hidden",
        "package com.example.target; class Hidden { public int value() { return 1; } }");
    inaccessible.put(
        "com.example.target.Factory",
        "package com.example.target; public class Factory { "
            + "public static Object create() { return new Hidden(); } }");
    inaccessible.put(
        "com.example.adapter.HiddenApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Hidden\") public interface HiddenApi { int value(); }");
    CompileTestHarness.RunnableResult inaccessibleResult =
        CompileTestHarness.compileAndLoad(inaccessible);
    assertTrue(inaccessibleResult.success, inaccessibleResult.errors());
    Class<?> factory = inaccessibleResult.loader.loadClass("com.example.target.Factory");
    Object hidden = factory.getMethod("create").invoke(null);
    java.lang.reflect.Method hiddenValue =
        inaccessibleResult
            .loader
            .loadClass("com.example.adapter.HiddenApi$Ext")
            .getMethod("value", Object.class);
    ExternalTypeResolutionException access =
        assertResolutionFailure(hiddenValue, hidden, "com.example.target.Hidden", "value");
    assertInstanceOf(IllegalAccessException.class, access.getCause());
  }

  private static Map<String, String> externalTypeSources(String apiMethod, String implementation) {
    return externalTypeSources(apiMethod, implementation, "value");
  }

  private static Map<String, String> chainSources(boolean staticParent) {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Child",
        "package com.example.target; public class Child { public String label() { return "
            + "String.valueOf(Child.class.getClassLoader()).contains(\"first\") ? \"first\" : "
            + "String.valueOf(Child.class.getClassLoader()).contains(\"second\") ? \"second\" : \"parent\"; } }");
    sources.put(
        "com.example.target.Parent",
        "package com.example.target; public class Parent { public "
            + (staticParent ? "static " : "")
            + "Child child() { return new Child(); } public String replace(Child child) { return child.label(); } }");
    sources.put(
        "com.example.adapter.ChildApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Child\") public interface ChildApi { String label(); }");
    sources.put(
        "com.example.adapter.ParentApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Parent\") public interface ParentApi { "
            + (staticParent ? "@ExternalType.Static " : "")
            + "@ExternalType.Type(ChildApi.class) Object child(); "
            + "String replace(@ExternalType.Type(ChildApi.class) Object child); }");
    return sources;
  }

  private static Map<String, String> externalTypeSources(
      String apiMethod, String implementation, String targetMethod) {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "com.example.target.Counter",
        "package com.example.target; public class Counter { public Counter() {} public "
            + (apiMethod.contains("@ExternalType.Static") ? "static " : "")
            + (implementation.contains("L;") ? "long " : "int ")
            + targetMethod
            + "() { "
            + implementation
            + " } }");
    sources.put(
        "com.example.target.Context",
        "package com.example.target; public class Context { public Context() {} }");
    sources.put(
        "com.example.adapter.CounterApi",
        "package com.example.adapter; import io.btrace.core.extensions.ExternalType; "
            + "@ExternalType(\"com.example.target.Counter\") public interface CounterApi { "
            + apiMethod
            + " }");
    return sources;
  }

  private static ExternalTypeResolutionException assertResolutionFailure(
      java.lang.reflect.Method method, Object target, String owner, String member) {
    InvocationTargetException failure =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    null, method.getParameterCount() == 0 ? new Object[0] : new Object[] {target}));
    assertInstanceOf(ExternalTypeResolutionException.class, failure.getCause());
    ExternalTypeResolutionException resolution =
        (ExternalTypeResolutionException) failure.getCause();
    assertEquals(
        "Unable to resolve @ExternalType " + owner + "#" + member, resolution.getMessage());
    return resolution;
  }

  private static int invokeStatic(
      java.lang.reflect.Method method,
      ClassLoader loader,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    ClassLoader saved = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(loader);
      ready.countDown();
      start.await();
      return (int) method.invoke(null);
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
  }

  private static int invokeExplicitStatic(
      java.lang.reflect.Method method,
      ClassLoader loader,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    return (int) method.invoke(null, loader);
  }

  private static int resolutionAttempts(Class<?> adapter, int ordinal) throws Exception {
    java.lang.reflect.Field attempts =
        adapter.getDeclaredField("__btraceExternalTypeResolutionAttempts$" + ordinal);
    attempts.setAccessible(true);
    return attempts.getInt(null);
  }

  private static int staticLoaderIndexEntries(Class<?> adapter, int ordinal) throws Exception {
    java.lang.reflect.Field loaderIndex = adapter.getDeclaredField("$" + ordinal + "$loaderIndex");
    loaderIndex.setAccessible(true);
    return ((Map<?, ?>) loaderIndex.get(null)).size();
  }

  private static final class MutableTargetLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes;
    private boolean visible;
    int targetLoads;

    MutableTargetLoader(CompileTestHarness.RunnableResult result, boolean visible) {
      super(result.loader);
      classBytes = result.classBytes;
      this.visible = visible;
    }

    void makeVisible() {
      visible = true;
    }

    @Override
    public boolean equals(Object other) {
      throw new AssertionError("adapter cache must not call ClassLoader.equals");
    }

    @Override
    public int hashCode() {
      throw new AssertionError("adapter cache must not call ClassLoader.hashCode");
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
      boolean counter = "com.example.target.Counter".equals(name);
      boolean context = "com.example.target.Context".equals(name);
      if (!counter && !context) return super.loadClass(name, resolve);
      if (counter) {
        targetLoads++;
        if (!visible) throw new ClassNotFoundException(name);
      }
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        byte[] bytes = classBytes.get(name);
        loaded = defineClass(name, bytes, 0, bytes.length);
      }
      if (resolve) resolveClass(loaded);
      return loaded;
    }
  }

  private static final class ChainLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes;
    private final String id;
    private boolean childVisible = true;

    ChainLoader(CompileTestHarness.RunnableResult result, String id) {
      super(result.loader);
      classBytes = result.classBytes;
      this.id = id;
    }

    void hideChild() {
      childVisible = false;
    }

    void showChild() {
      childVisible = true;
    }

    @Override
    public boolean equals(Object other) {
      throw new AssertionError("adapter cache must not call ClassLoader.equals");
    }

    @Override
    public int hashCode() {
      throw new AssertionError("adapter cache must not call ClassLoader.hashCode");
    }

    @Override
    public String toString() {
      return id;
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
      if (!"com.example.target.Parent".equals(name) && !"com.example.target.Child".equals(name)) {
        return super.loadClass(name, resolve);
      }
      if ("com.example.target.Child".equals(name) && !childVisible) throw new ClassNotFoundException(name);
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        byte[] bytes = classBytes.get(name);
        loaded = defineClass(name, bytes, 0, bytes.length);
      }
      if (resolve) resolveClass(loaded);
      return loaded;
    }
  }
}
