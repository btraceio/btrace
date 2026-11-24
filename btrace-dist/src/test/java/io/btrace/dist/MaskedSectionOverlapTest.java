/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * Checks that no class of the masked {@code btrace.jar} ships twice where only one copy can ever be
 * loaded.
 *
 * <p>The jar holds root classes ({@code .class} entries outside {@code META-INF/}) and three masked
 * sections, {@code META-INF/btrace/agent/}, {@code client/} and {@code shared/}, whose classes are
 * {@code .classdata} entries. {@code MaskedClassLoader} is parent-first, and the parent of every
 * section loader sees the root, so a masked copy of a root class is never loaded. Its {@code
 * findClass} looks in its own section and then in the shared section, so a shared class needs no
 * copy in the agent or client section. The test therefore fails when a class is both a root class
 * and in a masked section, or both in the shared section and in the agent or client section. A
 * class in both the agent and the client section is allowed: those are separate loaders, and a
 * class used by both stays out of the shared section unless it is placed there deliberately.
 *
 * <p>A {@code .classdata} entry under {@code META-INF/btrace/} outside the three sections fails,
 * because no loader reads it and this rule would not cover it. A jar with no root class or an empty
 * section fails too, so a layout this test no longer understands cannot pass by finding nothing.
 *
 * <p>The tests whose names start with {@code fixture} run the same rule on hand-made layouts, so
 * the gate is seen failing on bad input independently of the jar.
 */
class MaskedSectionOverlapTest {
  static final String ROOT = "root";
  static final String AGENT = "agent";
  static final String CLIENT = "client";
  static final String SHARED = "shared";

  private static final String SECTIONS_PREFIX = "META-INF/btrace/";
  private static final String CLASSDATA = ".classdata";

  @Test
  void noClassShipsInTwoPlacesThatHideEachOther() throws IOException {
    List<String> errors = overlapErrors(readLayout(BootstrapClosureTest.locateBTraceJar()));
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  // ---- fixtures for the gate's own logic ----

  @Test
  void fixtureRootClassWithAMaskedCopyFails() {
    Map<String, Set<String>> layout = layout();
    layout.get(ROOT).add("a/Root");
    layout.get(CLIENT).add("a/Root");
    assertEquals(
        Collections.singletonList(
            "a/Root is a root class and also in the client section; the parent loader always"
                + " finds the root copy, so drop the masked copy in btrace-dist/build.gradle"),
        overlapErrors(layout));
  }

  @Test
  void fixtureSharedClassWithASectionCopyFails() {
    Map<String, Set<String>> layout = layout();
    layout.get(SHARED).add("a/Shared");
    layout.get(AGENT).add("a/Shared");
    assertEquals(
        Collections.singletonList(
            "a/Shared is in the shared section and also in the agent section; the agent loader"
                + " checks its own section first, so the shared copy is shadowed; remove the"
                + " unintended duplicate in btrace-dist/build.gradle"),
        overlapErrors(layout));
  }

  @Test
  void fixtureAgentAndClientCopiesAreAllowed() {
    Map<String, Set<String>> layout = layout();
    layout.get(AGENT).add("a/Both");
    layout.get(CLIENT).add("a/Both");
    assertEquals(Collections.emptyList(), overlapErrors(layout));
  }

  @Test
  void fixtureEveryForbiddenPairIsReported() {
    Map<String, Set<String>> layout = layout();
    for (String section : Arrays.asList(ROOT, AGENT, CLIENT, SHARED)) {
      layout.get(section).add("a/Everywhere");
    }
    List<String> errors = overlapErrors(layout);
    assertEquals(5, errors.size(), String.join("\n", errors));
  }

  @Test
  void fixtureUnknownSectionFails() {
    Map<String, Set<String>> layout = layout();
    layout.put("extra", new TreeSet<>(Collections.singleton("a/Lost")));
    assertEquals(
        Collections.singletonList(
            "a/Lost is in the unknown section META-INF/btrace/extra/, which no loader reads"),
        overlapErrors(layout));
  }

  @Test
  void fixtureEmptySectionFails() {
    Map<String, Set<String>> layout = layout();
    layout.get(SHARED).clear();
    assertEquals(
        Collections.singletonList("btrace.jar has no shared classes; the layout is not understood"),
        overlapErrors(layout));
  }

  /** A layout in which every section holds one class of its own and nothing overlaps. */
  private static Map<String, Set<String>> layout() {
    Map<String, Set<String>> layout = new TreeMap<>();
    for (String section : Arrays.asList(ROOT, AGENT, CLIENT, SHARED)) {
      layout.put(section, new TreeSet<>(Collections.singleton("own/" + section + "/Only")));
    }
    return layout;
  }

  /**
   * Returns the classes of each place in the jar by internal name: {@link #ROOT} for root classes
   * and the section name for each {@code META-INF/btrace/<section>/} holding {@code .classdata}.
   */
  static Map<String, Set<String>> readLayout(File jar) throws IOException {
    Map<String, Set<String>> layout = new TreeMap<>();
    for (String section : Arrays.asList(ROOT, AGENT, CLIENT, SHARED)) {
      layout.put(section, new TreeSet<>());
    }
    try (JarFile jarFile = new JarFile(jar)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory()) {
          continue;
        }
        if (name.startsWith(SECTIONS_PREFIX) && name.endsWith(CLASSDATA)) {
          String rest = name.substring(SECTIONS_PREFIX.length());
          int slash = rest.indexOf('/');
          String section = slash < 0 ? "" : rest.substring(0, slash);
          String className = rest.substring(slash + 1, rest.length() - CLASSDATA.length());
          layout.computeIfAbsent(section, s -> new TreeSet<>()).add(className);
        } else if (!name.startsWith("META-INF/") && name.endsWith(".class")) {
          layout.get(ROOT).add(name.substring(0, name.length() - ".class".length()));
        }
      }
    }
    return layout;
  }

  /** Returns one message per class that ships in two places where one copy hides the other. */
  static List<String> overlapErrors(Map<String, Set<String>> layout) {
    List<String> errors = new ArrayList<>();
    for (String section : Arrays.asList(ROOT, AGENT, CLIENT, SHARED)) {
      Set<String> classes = layout.get(section);
      if (classes == null || classes.isEmpty()) {
        errors.add("btrace.jar has no " + section + " classes; the layout is not understood");
      }
    }
    for (Map.Entry<String, Set<String>> place : layout.entrySet()) {
      if (!Arrays.asList(ROOT, AGENT, CLIENT, SHARED).contains(place.getKey())) {
        for (String className : place.getValue()) {
          errors.add(
              className
                  + " is in the unknown section "
                  + SECTIONS_PREFIX
                  + place.getKey()
                  + "/, which no loader reads");
        }
      }
    }
    for (String section : Arrays.asList(AGENT, CLIENT, SHARED)) {
      for (String className : common(layout, ROOT, section)) {
        errors.add(
            className
                + " is a root class and also in the "
                + section
                + " section; the parent loader always finds the root copy, so drop the masked copy"
                + " in btrace-dist/build.gradle");
      }
    }
    for (String section : Arrays.asList(AGENT, CLIENT)) {
      for (String className : common(layout, SHARED, section)) {
        errors.add(
            className
                + " is in the shared section and also in the "
                + section
                + " section; the "
                + section
                + " loader checks its own section first, so the shared copy is shadowed; remove the"
                + " unintended duplicate in btrace-dist/build.gradle");
      }
    }
    return errors;
  }

  private static Set<String> common(Map<String, Set<String>> layout, String a, String b) {
    Set<String> result = new TreeSet<>(layout.getOrDefault(a, Collections.emptySet()));
    result.retainAll(layout.getOrDefault(b, Collections.emptySet()));
    return result;
  }
}
