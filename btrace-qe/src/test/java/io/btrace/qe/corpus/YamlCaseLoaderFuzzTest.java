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
package io.btrace.qe.corpus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Fuzzes {@link YamlCaseLoader}: whatever a case file contains, loading must either yield validated
 * cases or fail with a {@link CaseValidationException} that names the offending file.
 */
class YamlCaseLoaderFuzzTest {
  private static final String FILE_NAME = "fuzz-case.yaml";

  private static final String[] TOP = {
    "id", "surface", "intent", "tier", "title", "spec", "given", "steps", "expect"
  };
  private static final String[] NESTED = {
    "doc", "claim", "mode", "target", "agentArgs", "clientArgs", "exitCode", "messageMustMention"
  };
  private static final String[] UNKNOWN = {"extra", "foo", "Id", "steps ", "", "x.y", "é"};
  private static final String[] WORDS = {
    "resilience",
    "conformance",
    "RESILIENCE",
    "bogus",
    "smoke",
    "full",
    "target",
    "client",
    "launch",
    "awaitReady",
    "canary",
    "shutdown",
    "awaitExit",
    "runClient",
    "docs/x.md",
    "claim",
    "SteadyStateTarget",
    "-p",
    "",
    " ",
    "1",
    "true",
    "null",
    "~",
    "a: b",
    "- c",
    "#d",
    "&e",
    "*f",
    "{",
    "[",
    "é日😀",
    "line\nbreak",
    "tab\tchar"
  };

  /** An ordered key/value list; unlike a map it can hold duplicate keys. */
  private static final class Pairs {
    final List<Object[]> entries = new ArrayList<>();

    Pairs put(Object key, Object value) {
      entries.add(new Object[] {key, value});
      return this;
    }
  }

  /** A generated YAML document and whether it deliberately repeats a mapping key. */
  private static final class Doc {
    final String text;
    final boolean duplicateKey;

    Doc(String text, boolean duplicateKey) {
      this.text = text;
      this.duplicateKey = duplicateKey;
    }

    @Override
    public String toString() {
      return text + (duplicateKey ? "  [contains duplicate key]" : "");
    }
  }

  // ---- serialization (flow-style YAML, which is a superset of JSON) ----

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\') {
        sb.append('\\').append(c);
      } else if (c < 0x20) {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.append('"').toString();
  }

  /** Serializes a value; sets {@code dup[0]} when a mapping repeats a key. */
  private static void write(Object value, StringBuilder out, boolean[] dup) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String) {
      out.append(quote((String) value));
    } else if (value instanceof Raw) {
      out.append(((Raw) value).text);
    } else if (value instanceof List) {
      out.append('[');
      String sep = "";
      for (Object item : (List<?>) value) {
        out.append(sep);
        write(item, out, dup);
        sep = ", ";
      }
      out.append(']');
    } else if (value instanceof Pairs) {
      out.append('{');
      Set<String> seen = new HashSet<>();
      String sep = "";
      for (Object[] e : ((Pairs) value).entries) {
        StringBuilder key = new StringBuilder();
        write(e[0], key, dup);
        if (!seen.add(key.toString())) {
          dup[0] = true;
        }
        out.append(sep).append(key).append(": ");
        write(e[1], out, dup);
        sep = ", ";
      }
      out.append('}');
    } else {
      out.append(value);
    }
  }

  /** A literal YAML fragment (numbers, booleans). */
  private static final class Raw {
    final String text;

    Raw(String text) {
      this.text = text;
    }
  }

  // ---- generation ----

  private static <T> T pick(Random rnd, T[] items) {
    return items[rnd.nextInt(items.length)];
  }

  private static Object randomValue(Random rnd, int depth) {
    int kinds = depth <= 0 ? 6 : 8;
    switch (rnd.nextInt(kinds)) {
      case 0:
        return pick(rnd, WORDS);
      case 1:
        return new Raw(String.valueOf(rnd.nextInt(2000) - 1000));
      case 2:
        return new Raw(pick(rnd, new String[] {"99999999999", "-99999999999", "1.5", "0x10"}));
      case 3:
        return new Raw(rnd.nextBoolean() ? "true" : "false");
      case 4:
        return null;
      case 5:
        return pick(rnd, WORDS) + rnd.nextInt(100);
      case 6:
        List<Object> list = new ArrayList<>();
        for (int n = rnd.nextInt(4); n > 0; n--) {
          list.add(randomValue(rnd, depth - 1));
        }
        return list;
      default:
        Pairs map = new Pairs();
        for (int n = rnd.nextInt(4); n > 0; n--) {
          map.put(
              rnd.nextBoolean() ? pick(rnd, NESTED) : pick(rnd, UNKNOWN),
              randomValue(rnd, depth - 1));
        }
        return map;
    }
  }

  private static Object randomKey(Random rnd) {
    switch (rnd.nextInt(12)) {
      case 0:
        return new Raw(String.valueOf(rnd.nextInt(10)));
      case 1:
        return new Raw("true");
      case 2:
        return null;
      case 3:
        return pick(rnd, UNKNOWN);
      default:
        return pick(rnd, TOP);
    }
  }

  private static List<Object> strings(String... items) {
    return new ArrayList<Object>(Arrays.asList(items));
  }

  /** A document the loader accepts, in one of the two modes. */
  private static Pairs validBase(Random rnd) {
    Pairs doc = new Pairs();
    boolean conformance = rnd.nextBoolean();
    boolean client = rnd.nextBoolean();
    doc.put("id", "fuzz.case" + rnd.nextInt(1000));
    doc.put("surface", "fuzz");
    doc.put("intent", conformance ? "conformance" : "resilience");
    if (rnd.nextBoolean()) {
      doc.put("tier", rnd.nextBoolean() ? "smoke" : "full");
    }
    doc.put("title", "fuzz title");
    if (conformance) {
      doc.put("spec", new Pairs().put("doc", "docs/x.md").put("claim", "a claim"));
    }
    if (client) {
      doc.put(
          "given",
          new Pairs().put("mode", "client").put("clientArgs", strings("-p", "notanumber")));
      doc.put("steps", strings("runClient", "awaitExit"));
    } else {
      doc.put(
          "given",
          new Pairs()
              .put("mode", "target")
              .put("target", "SteadyStateTarget")
              .put("agentArgs", "debug=true"));
      doc.put("steps", strings("launch", "awaitReady", "canary", "shutdown", "awaitExit"));
    }
    Pairs expect = new Pairs();
    if (conformance && rnd.nextBoolean()) {
      expect.put("exitCode", new Raw(String.valueOf(rnd.nextInt(3))));
    }
    expect.put("messageMustMention", strings("mention"));
    doc.put("expect", expect);
    return doc;
  }

  /** Applies one random structural mutation somewhere in the document. */
  private static void mutate(Random rnd, Pairs doc) {
    Pairs target = doc;
    if (rnd.nextInt(3) == 0) {
      List<Pairs> nested = new ArrayList<>();
      for (Object[] e : doc.entries) {
        if (e[1] instanceof Pairs) {
          nested.add((Pairs) e[1]);
        }
      }
      if (!nested.isEmpty()) {
        target = nested.get(rnd.nextInt(nested.size()));
      }
    }
    switch (rnd.nextInt(5)) {
      case 0: // replace a value with a random one
        if (!target.entries.isEmpty()) {
          target.entries.get(rnd.nextInt(target.entries.size()))[1] = randomValue(rnd, 2);
        }
        break;
      case 1: // drop an entry
        if (!target.entries.isEmpty()) {
          target.entries.remove(rnd.nextInt(target.entries.size()));
        }
        break;
      case 2: // unknown or odd key
        target.put(randomKey(rnd), randomValue(rnd, 2));
        break;
      case 3: // duplicate an existing key, usually with a different value
        if (!target.entries.isEmpty()) {
          Object[] e = target.entries.get(rnd.nextInt(target.entries.size()));
          target.put(e[0], rnd.nextBoolean() ? e[1] : randomValue(rnd, 1));
        }
        break;
      default: // schema key with a random value
        target.put(pick(rnd, rnd.nextBoolean() ? TOP : NESTED), randomValue(rnd, 2));
    }
  }

  private static Doc generate(Random rnd) {
    int shape = rnd.nextInt(20);
    if (shape == 0) { // non-mapping documents
      String[] roots = {"", "[]", "[1, 2]", "\"just a string\"", "42", "null", "---\n---", "# c"};
      return new Doc(pick(rnd, roots), false);
    }
    Pairs doc = validBase(rnd);
    int mutations = rnd.nextInt(4);
    for (int i = 0; i < mutations; i++) {
      mutate(rnd, doc);
    }
    boolean[] dup = new boolean[1];
    StringBuilder text = new StringBuilder();
    write(doc, text, dup);
    if (shape == 1) { // syntactically broken: truncate or inject noise
      String s = text.toString();
      if (rnd.nextBoolean()) {
        s = s.substring(0, rnd.nextInt(s.length() + 1));
      } else {
        String noise =
            pick(rnd, new String[] {"\t", ": :", "{{", "]", "\"", "&a *b", "%", "\u0000", "- -"});
        int at = rnd.nextInt(s.length() + 1);
        s = s.substring(0, at) + noise + s.substring(at);
      }
      // a broken document may or may not still parse; the duplicate flag is only trusted when it
      // does
      return new Doc(s, false);
    }
    return new Doc(text.toString(), dup[0]);
  }

  // ---- properties ----

  private static void check(Path dir, Doc doc, AtomicInteger loaded, AtomicInteger rejected)
      throws IOException {
    Path file = dir.resolve(FILE_NAME);
    Files.write(file, doc.text.getBytes(StandardCharsets.UTF_8));
    try {
      List<CaseDef> cases = new YamlCaseLoader().loadAll(dir);
      assertTrue(!doc.duplicateKey, "a document with a duplicate key must be rejected");
      assertTrue(cases != null && cases.size() == 1, "expected exactly one case");
      loaded.incrementAndGet();
    } catch (CaseValidationException e) {
      assertTrue(
          e.getMessage() != null && e.getMessage().contains(FILE_NAME),
          "CaseValidationException must name the file, was: " + e.getMessage());
      rejected.incrementAndGet();
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void loadAllEitherLoadsOrRejectsWithTheFileNamed() throws Exception {
    Path dir = Files.createTempDirectory("qe-yaml-fuzz");
    AtomicInteger loaded = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    try {
      PropertyRunner.forAll(
          "YamlCaseLoader fuzz",
          YamlCaseLoaderFuzzTest::generate,
          doc -> check(dir, doc, loaded, rejected));
    } finally {
      Files.deleteIfExists(dir);
    }
    if (PropertyRunner.iterations() >= 200) {
      // guard against a vacuous fuzz run: both outcomes must actually be exercised
      assertTrue(loaded.get() > 0, "no generated document loaded successfully");
      assertTrue(rejected.get() > 0, "no generated document was rejected");
    }
  }

  @Test
  void duplicateKeysAreRejectedAtTopLevelAndNested() {
    PropertyRunner.forAll(
        "duplicate key rejection",
        rnd -> {
          Pairs doc = validBase(rnd);
          Pairs target = doc;
          if (rnd.nextBoolean()) {
            for (Object[] e : doc.entries) {
              if (e[1] instanceof Pairs) {
                target = (Pairs) e[1];
              }
            }
          }
          Object[] victim = target.entries.get(rnd.nextInt(target.entries.size()));
          // same key, same or different value: both are ambiguous and must be refused
          target.put(victim[0], rnd.nextBoolean() ? victim[1] : "other");
          boolean[] dup = new boolean[1];
          StringBuilder text = new StringBuilder();
          write(doc, text, dup);
          return new Doc(text.toString(), dup[0]);
        },
        doc -> {
          Path dir = Files.createTempDirectory("qe-yaml-dup");
          try {
            check(dir, doc, new AtomicInteger(), new AtomicInteger());
          } finally {
            Files.deleteIfExists(dir.resolve(FILE_NAME));
            Files.deleteIfExists(dir);
          }
        });
  }
}
