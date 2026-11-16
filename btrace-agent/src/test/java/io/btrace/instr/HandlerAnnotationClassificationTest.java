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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Sampled;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

/**
 * Every annotation of {@code io.btrace.core.annotations} that can be put on a method is classified
 * by the preprocessor: it is in {@code RT_AWARE_ANNOTS} or {@code GUARDED_ANNOTS}, it is not a
 * handler annotation at all ({@link #NOT_HANDLERS}), or it is listed with a reason in {@code
 * handler-annotation-baseline.txt}.
 *
 * <p>Both exception lists ratchet: an entry that became classified, or whose annotation is gone,
 * fails as stale, so neither list can drift silently. A new method annotation that is in none of
 * them fails as new.
 */
class HandlerAnnotationClassificationTest {
  private static final String ANNOTATION_PACKAGE = "io.btrace.core.annotations";
  private static final String BASELINE = "/handler-annotation-baseline.txt";

  /**
   * Method-targeting annotations that are not handlers, so the preprocessor never classifies them.
   */
  private static final List<Class<?>> NOT_HANDLERS =
      Arrays.<Class<?>>asList(
          // A value nested inside @OnMethod(location = @Location(...)); BTraceMethodNode reads it
          // while visiting the @OnMethod annotation.
          Location.class,
          // A modifier on an @OnMethod handler; BTraceMethodNode reads it only when an @OnMethod
          // is present on the same method.
          Sampled.class);

  @Test
  void everyMethodAnnotationIsClassified() throws Exception {
    List<Class<?>> methodAnnotations = methodTargetingAnnotations();
    assertFalse(
        methodAnnotations.isEmpty(),
        "no method-targeting annotation found in " + ANNOTATION_PACKAGE + ": nothing was checked");
    assertTrue(methodAnnotations.contains(OnMethod.class), "@OnMethod missing from the scan");

    TreeMap<String, Class<?>> byName = new TreeMap<>();
    for (Class<?> a : methodAnnotations) {
      byName.put(a.getSimpleName(), a);
    }

    TreeSet<String> baseline = baselineEntries();
    TreeSet<String> unclassified = new TreeSet<>();
    TreeSet<String> stale = new TreeSet<>();

    for (Class<?> notHandler : NOT_HANDLERS) {
      String name = notHandler.getSimpleName();
      if (!byName.containsKey(name)) {
        stale.add("NOT_HANDLERS lists @" + name + ", which no longer targets methods");
      } else if (isInPreprocessorSets(notHandler)) {
        stale.add("NOT_HANDLERS lists @" + name + ", which the preprocessor classifies now");
      }
    }
    for (String entry : baseline) {
      Class<?> a = byName.get(entry);
      if (a == null) {
        stale.add("baseline lists @" + entry + ", which no longer targets methods");
      } else if (isInPreprocessorSets(a) || NOT_HANDLERS.contains(a)) {
        stale.add("baseline lists @" + entry + ", which is classified now");
      }
    }
    for (Class<?> a : methodAnnotations) {
      if (!isInPreprocessorSets(a)
          && !NOT_HANDLERS.contains(a)
          && !baseline.contains(a.getSimpleName())) {
        unclassified.add("@" + a.getSimpleName());
      }
    }

    assertTrue(
        unclassified.isEmpty(),
        "method annotation(s) in neither Preprocessor.RT_AWARE_ANNOTS nor GUARDED_ANNOTS (add them "
            + "there, or to NOT_HANDLERS if they are not handlers): "
            + unclassified);
    assertTrue(stale.isEmpty(), "stale exception entries (remove them): " + stale);
  }

  private static boolean isInPreprocessorSets(Class<?> annotation) {
    String desc = Type.getDescriptor(annotation);
    return Preprocessor.RT_AWARE_ANNOTS.contains(desc)
        || Preprocessor.GUARDED_ANNOTS.contains(desc);
  }

  private static List<Class<?>> methodTargetingAnnotations()
      throws IOException, URISyntaxException, ClassNotFoundException {
    List<Class<?>> result = new ArrayList<>();
    for (String className : annotationPackageClassNames()) {
      Class<?> c = Class.forName(className, false, OnMethod.class.getClassLoader());
      Target target = c.getAnnotation(Target.class);
      if (c.isAnnotation()
          && target != null
          && Arrays.asList(target.value()).contains(ElementType.METHOD)) {
        result.add(c);
      }
    }
    return result;
  }

  /** Top-level classes of the annotation package, read from the jar or directory holding it. */
  private static List<String> annotationPackageClassNames() throws IOException, URISyntaxException {
    File location =
        new File(OnMethod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    String dir = ANNOTATION_PACKAGE.replace('.', '/') + "/";
    List<String> names = new ArrayList<>();
    if (location.isDirectory()) {
      File[] files = new File(location, dir).listFiles();
      assertNotNull(files, "no directory " + dir + " under " + location);
      for (File f : files) {
        addClassName(names, dir + f.getName());
      }
    } else {
      try (JarFile jar = new JarFile(location)) {
        for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
          String entry = e.nextElement().getName();
          if (entry.startsWith(dir) && entry.indexOf('/', dir.length()) < 0) {
            addClassName(names, entry);
          }
        }
      }
    }
    Collections.sort(names);
    return names;
  }

  private static void addClassName(List<String> names, String path) {
    if (path.endsWith(".class") && path.indexOf('$') < 0) {
      names.add(path.substring(0, path.length() - ".class".length()).replace('/', '.'));
    }
  }

  private static TreeSet<String> baselineEntries() throws IOException {
    TreeSet<String> entries = new TreeSet<>();
    try (InputStream in = HandlerAnnotationClassificationTest.class.getResourceAsStream(BASELINE)) {
      assertNotNull(in, BASELINE + " is not on the test classpath");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      for (String line : new String(out.toByteArray(), StandardCharsets.UTF_8).split("\\R")) {
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("#")) {
          continue;
        }
        int hash = s.indexOf('#');
        assertTrue(hash > 0, "baseline entry has no '# reason': " + line);
        entries.add(s.substring(0, hash).trim());
      }
    }
    return entries;
  }
}
