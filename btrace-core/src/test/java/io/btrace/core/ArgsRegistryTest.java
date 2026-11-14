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
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every agent option declared in {@link Args} is documented in the agent usage text and in
 * docs/QuickReference.md, or is listed in {@code args-registry-baseline.txt} with a reason.
 *
 * <p>The baseline follows the project's ratchet rule: an option that is undocumented and not listed
 * fails as new, and a listed entry that is documented now (or whose option is gone) fails as stale.
 * Entries read {@code usage <option>} or {@code quickref <option>}, optionally followed by {@code #
 * reason}. An option counts as documented by exactly these rules:
 *
 * <ul>
 *   <li>usage text (the loaded {@code btrace.agent.usage} value: the properties loader has already
 *       resolved {@code \n} and dropped the indentation of continuation lines, so every option line
 *       reads {@code "\n name description"}): the name is a list head, that is, one of the
 *       comma-separated names that start a line indented by exactly two spaces and are followed by
 *       whitespace, as in {@code port command server port} or {@code grant, deny, grantAll accepted
 *       ...}. The usage sentence and header (no indentation) and the same word inside a description
 *       do not count;
 *   <li>QuickReference: the name is followed directly by {@code =} and is not the tail of a longer
 *       identifier, as in {@code `port=<port>`}. The bare word in prose, or {@code
 *       scriptOutputFile=} for {@code output}, does not count.
 * </ul>
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
    assertDocumented("usage", usageText(), ArgsRegistryTest::inUsage);
  }

  @Test
  void everyOptionIsInTheQuickReference() throws Exception {
    assertTrue(
        Files.isRegularFile(QUICK_REFERENCE),
        "QuickReference not found at " + QUICK_REFERENCE.toAbsolutePath().normalize());
    String text = new String(Files.readAllBytes(QUICK_REFERENCE), StandardCharsets.UTF_8);
    assertFalse(text.trim().isEmpty(), "QuickReference is empty");
    assertDocumented("quickref", text, ArgsRegistryTest::inQuickReference);
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

  private static final Pattern USAGE_HEAD =
      Pattern.compile("^ {2}([A-Za-z]\\w*(?:, *[A-Za-z]\\w*)*)(?=\\s)", Pattern.MULTILINE);

  /** Whether {@code option} is a list head in the usage text; see the class comment. */
  static boolean inUsage(String usage, String option) {
    Matcher m = USAGE_HEAD.matcher(usage);
    while (m.find()) {
      for (String name : m.group(1).split(",")) {
        if (name.trim().equals(option)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether {@code option=} appears in the text as a whole identifier; see the class comment. */
  static boolean inQuickReference(String text, String option) {
    return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(option) + "=").matcher(text).find();
  }

  @Test
  void usageMatcherAcceptsListHeadsOnly() {
    String usage =
        "Usage: java -javaagent:btrace.jar=<arguments>\n"
            + "where arguments is name=value pairs. output is mentioned here\n"
            + "output at the start of an unindented line\n"
            + "  port             command server port; the output goes to stdout\n"
            + "  grant, deny, grantAll  accepted for compatibility only\n"
            + "  (use allowExtensions or the output file)\n"
            + "  scriptOutputFile the path to a file\n";
    assertTrue(inUsage(usage, "port"), "list head");
    assertTrue(inUsage(usage, "scriptOutputFile"), "list head");
    assertTrue(inUsage(usage, "grant"), "first of a comma head");
    assertTrue(inUsage(usage, "deny"), "middle of a comma head");
    assertTrue(inUsage(usage, "grantAll"), "last of a comma head");
    assertFalse(inUsage(usage, "output"), "word in descriptions and prose lines only");
    assertFalse(inUsage(usage, "allowExtensions"), "word in a description line only");
    assertFalse(inUsage(usage, "por"), "prefix of a head");
    assertFalse(inUsage(usage, "Usage"), "first line is not an option list");
  }

  @Test
  void quickReferenceMatcherNeedsNameEquals() {
    String text =
        "- `port=<port>` - the output goes to stdout\n"
            + "- `scriptOutputFile=<file>`, `dumpDir=<dir>`\n"
            + "java -javaagent:btrace.jar=script=MyTrace.class,noServer=true MyApp\n";
    assertTrue(inQuickReference(text, "port"));
    assertTrue(inQuickReference(text, "dumpDir"));
    assertTrue(inQuickReference(text, "noServer"));
    assertTrue(inQuickReference(text, "script"));
    assertFalse(inQuickReference(text, "output"), "bare word in prose");
    assertFalse(inQuickReference(text, "Output"), "suffix of scriptOutputFile=");
    assertFalse(inQuickReference(text, "File"), "suffix of scriptOutputFile=");
  }

  private static void assertDocumented(
      String surface, String text, BiPredicate<String, String> documentedIn) throws Exception {
    Map<String, String> options = options();
    assertFalse(options.isEmpty(), "no public static final String constants found in Args");
    TreeSet<String> missing = new TreeSet<>();
    for (String option : options.values()) {
      if (!documentedIn.test(text, option)) {
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
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
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
