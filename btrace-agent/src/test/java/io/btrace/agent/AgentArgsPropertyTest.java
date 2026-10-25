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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.btrace.core.ArgsMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Seeded property tests for {@link Main#parseAgentArgs} and {@link Main#parseFlag}. */
class AgentArgsPropertyTest {
  private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
  private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String VALUE_CHARS = LOWER + UPPER + "0123456789$:./ _-éü日";
  // Characters for the "anything goes" input; the separators are heavily over-represented.
  private static final String NOISE = LOWER.substring(0, 6) + " $,=,=:é日ł😀";
  private static final List<String> LIST_KEYS =
      Arrays.asList("grant", "deny", "allowExtensions", "denyExtensions", "probes");

  private static String randomString(Random rnd, String alphabet, int minLen, int maxLen) {
    int len = minLen + rnd.nextInt(maxLen - minLen + 1);
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  /** Comma/equals-free value without leading or trailing whitespace (the parser trims). */
  private static String value(Random rnd, int minLen) {
    String v = randomString(rnd, VALUE_CHARS, minLen, 8).trim();
    return v.length() >= minLen ? v : randomString(rnd, LOWER, Math.max(minLen, 1), 4);
  }

  @Test
  void parseAgentArgsNeverThrowsOnArbitraryInput() {
    PropertyRunner.forAll(
        "parseAgentArgs totality",
        rnd -> randomString(rnd, NOISE, 0, 40),
        input -> {
          ArgsMap map = Main.parseAgentArgs(input);
          assertNotNull(map);
          for (Map.Entry<String, String> e : map) {
            assertNotNull(e.getKey());
            assertNotNull(e.getValue());
          }
        });
  }

  /** A generated argument string together with the map it must parse to. */
  private static final class Spec {
    final String line;
    final Map<String, String> expected;

    Spec(String line, Map<String, String> expected) {
      this.line = line;
      this.expected = expected;
    }

    @Override
    public String toString() {
      return line + "  => expected " + expected;
    }
  }

  @Test
  void wellFormedScalarPairsRoundTrip() {
    PropertyRunner.forAll(
        "scalar key=value round trip",
        rnd -> {
          // n >= 1: the empty string parses to a single empty-named entry, not an empty map
          int n = 1 + rnd.nextInt(8);
          Map<String, String> expected = new LinkedHashMap<>();
          List<String> tokens = new ArrayList<>();
          while (expected.size() < n) {
            // 'k' prefix keeps scalar keys disjoint from list-valued keys and from "help"
            String key = "k" + randomString(rnd, LOWER + UPPER + "0123456789", 0, 6);
            if (!expected.containsKey(key)) {
              String value = rnd.nextInt(4) == 0 ? "" : value(rnd, 1);
              expected.put(key, value);
              tokens.add(key + "=" + value);
            }
          }
          return new Spec(String.join(",", tokens), expected);
        },
        spec -> {
          ArgsMap map = Main.parseAgentArgs(spec.line);
          assertEquals(spec.expected.size(), map.size());
          int idx = 0;
          for (Map.Entry<String, String> e : spec.expected.entrySet()) {
            assertEquals(e.getValue(), map.get(e.getKey()), "value of " + e.getKey());
            assertEquals(e.getKey() + "=" + e.getValue(), map.get(idx++), "order");
          }
        });
  }

  @Test
  void listValuedKeysAbsorbBareTokensUntilNextKeyValueToken() {
    PropertyRunner.forAll(
        "list-valued key absorption",
        rnd -> {
          List<String> listKeys = new ArrayList<>(LIST_KEYS);
          Map<String, String> expected = new LinkedHashMap<>();
          List<String> tokens = new ArrayList<>();
          int segments = 1 + rnd.nextInt(6);
          int scalarSeq = 0;
          for (int s = 0; s < segments; s++) {
            boolean list = !listKeys.isEmpty() && rnd.nextBoolean();
            if (list) {
              String key = listKeys.remove(rnd.nextInt(listKeys.size()));
              StringBuilder joined = new StringBuilder(value(rnd, 1));
              tokens.add(key + "=" + joined);
              int bare = rnd.nextInt(4);
              for (int b = 0; b < bare; b++) {
                // upper-case only: can never equal a key name or "help", and has no '=' or ','
                String token = randomString(rnd, UPPER + "_", 1, 8);
                tokens.add(rnd.nextInt(3) == 0 ? " " + token + " " : token);
                joined.append(',').append(token);
              }
              expected.put(key, joined.toString());
            } else {
              String key = "k" + (scalarSeq++);
              String value = value(rnd, 0);
              tokens.add(key + "=" + value);
              expected.put(key, value);
            }
          }
          return new Spec(String.join(",", tokens), expected);
        },
        spec -> {
          ArgsMap map = Main.parseAgentArgs(spec.line);
          assertEquals(spec.expected.size(), map.size(), "number of keys");
          for (Map.Entry<String, String> e : spec.expected.entrySet()) {
            assertEquals(e.getValue(), map.get(e.getKey()), "value of " + e.getKey());
          }
        });
  }

  @Test
  void helpAfterAListStaysItsOwnKey() {
    PropertyRunner.forAll(
        "help is never absorbed",
        rnd -> LIST_KEYS.get(rnd.nextInt(LIST_KEYS.size())) + "=" + value(rnd, 1) + ",help",
        line -> {
          ArgsMap map = Main.parseAgentArgs(line);
          assertEquals("", map.get("help"));
          assertEquals(2, map.size());
        });
  }

  @Test
  void parseFlagIsTotalAndMatchesTheDocumentedLiterals() {
    PropertyRunner.forAll(
        "parseFlag totality",
        rnd -> randomString(rnd, NOISE + "truefalse", 0, 8),
        input -> {
          Boolean flag = Main.parseFlag(input);
          Boolean expected =
              input.isEmpty() || input.toLowerCase(Locale.ROOT).equals("true")
                  ? Boolean.TRUE
                  : input.toLowerCase(Locale.ROOT).equals("false") ? Boolean.FALSE : null;
          assertEquals(expected, flag);
        });
  }

  @Test
  void parseFlagAcceptsAnyCaseOfTrueAndFalse() {
    PropertyRunner.forAll(
        "parseFlag case-insensitivity",
        rnd -> {
          String base = rnd.nextBoolean() ? "true" : "false";
          StringBuilder sb = new StringBuilder();
          for (char c : base.toCharArray()) {
            sb.append(rnd.nextBoolean() ? Character.toUpperCase(c) : c);
          }
          return sb.toString();
        },
        input ->
            assertEquals(
                input.toLowerCase(Locale.ROOT).equals("true") ? Boolean.TRUE : Boolean.FALSE,
                Main.parseFlag(input)));
  }

  @Test
  void parseFlagRejectsNearMisses() {
    PropertyRunner.forAll(
        "parseFlag near misses",
        rnd -> {
          String[] base = {"true", "false"};
          String word = base[rnd.nextInt(2)];
          switch (rnd.nextInt(4)) {
            case 0:
              return word + randomString(rnd, LOWER + " 01", 1, 2);
            case 1:
              return randomString(rnd, " \t", 1, 2) + word;
            case 2:
              return word.substring(0, word.length() - 1);
            default:
              return "yes";
          }
        },
        input -> assertNull(Main.parseFlag(input)));
  }
}
