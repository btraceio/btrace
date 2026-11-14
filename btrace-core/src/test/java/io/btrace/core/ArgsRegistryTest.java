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
package io.btrace.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every agent option declared in {@link Args} is documented in the agent usage text and in
 * docs/QuickReference.md, or is listed in {@code args-registry-baseline.txt} with a reason.
 *
 * <p>The baseline follows the project's ratchet rule: an option that is undocumented and not listed
 * fails as new, and a listed entry that is documented now (or whose option is gone) fails as stale.
 * Entries read {@code usage <option>} or {@code quickref <option>}, optionally followed by {@code #
 * reason}. An option counts as documented when its name appears as a whole word: the usage text
 * writes it as {@code name description} and QuickReference as {@code name=} or {@code `name=`}.
 *
 * <p>Gradle runs tests with the module directory as working directory, so QuickReference is found
 * at {@code ../docs/QuickReference.md}.
 */
class ArgsRegistryTest {
  private static final String USAGE_KEY = "btrace.agent.usage";
  private static final String MESSAGES = "io/btrace/core/messages.properties";
  private static final String BASELINE = "/args-registry-baseline.txt";
  private static final Path QUICK_REFERENCE = Paths.get("..", "docs", "QuickReference.md");

  @Test
  void everyOptionIsInTheUsageText() throws Exception {
    assertDocumented("usage", usageText());
  }

  @Test
  void everyOptionIsInTheQuickReference() throws Exception {
    assertTrue(
        Files.isRegularFile(QUICK_REFERENCE),
        "QuickReference not found at " + QUICK_REFERENCE.toAbsolutePath().normalize());
    String text = new String(Files.readAllBytes(QUICK_REFERENCE), StandardCharsets.UTF_8);
    assertFalse(text.trim().isEmpty(), "QuickReference is empty");
    assertDocumented("quickref", text);
  }

  @Test
  void baselineEntriesNameAKnownSurfaceAndGiveAReason() throws Exception {
    List<String> problems = new ArrayList<>();
    for (String line : baselineLines()) {
      String entry = stripReason(line);
      if (entry.isEmpty()) {
        continue;
      }
      if (!(entry.startsWith("usage ") || entry.startsWith("quickref "))
          || entry.split("\\s+").length != 2) {
        problems.add("not '<usage|quickref> <option>': " + line);
      } else if (!line.contains("#")) {
        problems.add("no '# reason': " + line);
      }
    }
    assertTrue(problems.isEmpty(), "malformed baseline entries: " + problems);
  }

  private static void assertDocumented(String surface, String text) throws Exception {
    Map<String, String> options = options();
    assertFalse(options.isEmpty(), "no public static final String constants found in Args");
    TreeSet<String> missing = new TreeSet<>();
    for (String option : options.values()) {
      if (!Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(option) + "(?![A-Za-z0-9_])")
          .matcher(text)
          .find()) {
        missing.add(surface + " " + option);
      }
    }
    TreeSet<String> listed = new TreeSet<>();
    for (String line : baselineLines()) {
      String entry = stripReason(line);
      if (entry.startsWith(surface + " ")) {
        listed.add(entry);
      }
    }
    TreeSet<String> added = new TreeSet<>(missing);
    added.removeAll(listed);
    TreeSet<String> stale = new TreeSet<>(listed);
    stale.removeAll(missing);
    assertTrue(
        added.isEmpty(),
        "option(s) of io.btrace.core.Args not documented (document them, or list them with a "
            + "reason in args-registry-baseline.txt): "
            + added);
    assertTrue(
        stale.isEmpty(),
        "stale entries in args-registry-baseline.txt (the option is documented now or gone; "
            + "remove the entry): "
            + stale);
  }

  /** Constant name to option name, for every public static final String of Args. */
  private static Map<String, String> options() throws IllegalAccessException {
    Map<String, String> options = new TreeMap<>();
    for (Field f : Args.class.getDeclaredFields()) {
      int m = f.getModifiers();
      if (Modifier.isPublic(m)
          && Modifier.isStatic(m)
          && Modifier.isFinal(m)
          && f.getType() == String.class) {
        options.put(f.getName(), (String) f.get(null));
      }
    }
    return options;
  }

  private static String usageText() throws IOException {
    Properties messages = new Properties();
    try (InputStream in = ArgsRegistryTest.class.getClassLoader().getResourceAsStream(MESSAGES)) {
      assertNotNull(in, MESSAGES + " is not on the classpath");
      messages.load(in);
    }
    String usage = messages.getProperty(USAGE_KEY);
    assertNotNull(usage, USAGE_KEY + " is not in " + MESSAGES);
    assertFalse(usage.trim().isEmpty(), USAGE_KEY + " is empty");
    return usage;
  }

  private static List<String> baselineLines() throws IOException {
    try (InputStream in = ArgsRegistryTest.class.getResourceAsStream(BASELINE)) {
      assertNotNull(in, BASELINE + " is not on the test classpath");
      byte[] buf = new byte[in.available() > 0 ? in.available() : 1024];
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      List<String> lines = new ArrayList<>();
      Collections.addAll(lines, new String(out.toByteArray(), StandardCharsets.UTF_8).split("\\R"));
      return lines;
    }
  }

  private static String stripReason(String line) {
    String s = line.trim();
    if (s.startsWith("#")) {
      return "";
    }
    int hash = s.indexOf('#');
    return (hash < 0 ? s : s.substring(0, hash)).trim();
  }
}
