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

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.SpecRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

public final class YamlCaseLoader {
  private static final Set<String> TOP_KEYS =
      new HashSet<>(
          Arrays.asList(
              "id", "surface", "intent", "title", "spec", "given", "steps", "expect", "tier"));
  private static final Set<String> SPEC_KEYS = new HashSet<>(Arrays.asList("doc", "claim"));
  private static final Set<String> GIVEN_KEYS =
      new HashSet<>(Arrays.asList("mode", "target", "agentArgs", "clientArgs"));
  private static final Set<String> EXPECT_KEYS =
      new HashSet<>(Arrays.asList("exitCode", "messageMustMention"));

  public List<CaseDef> loadAll(Path corpusDir) throws IOException {
    if (!Files.isDirectory(corpusDir)) {
      throw new IOException("case corpus directory does not exist: " + corpusDir);
    }
    List<Path> files;
    try (Stream<Path> walk = Files.walk(corpusDir)) {
      files =
          walk.filter(p -> p.toString().endsWith(".yaml")).sorted().collect(Collectors.toList());
    }
    List<CaseDef> result = new ArrayList<>();
    Yaml yaml = new Yaml();
    for (Path file : files) {
      Object loaded;
      try (InputStream in = Files.newInputStream(file)) {
        loaded = yaml.load(in);
      }
      CaseDef def = parse(file, asMap(file, "document", loaded, TOP_KEYS));
      def.validate();
      result.add(def);
    }
    return result;
  }

  private CaseDef parse(Path file, Map<String, Object> raw) {
    String id = requireString(file, raw, "id");
    String surface = requireString(file, raw, "surface");
    Intent intent = Intent.valueOf(requireString(file, raw, "intent").toUpperCase(Locale.ROOT));
    String title = requireString(file, raw, "title");
    SpecRef spec = parseSpec(file, raw.get("spec"));
    GivenDef given = parseGiven(file, raw.get("given"));
    List<String> steps = requireStringList(file, raw, "steps");
    ExpectationDef expect = parseExpect(file, raw.get("expect"));
    String tier = raw.containsKey("tier") ? String.valueOf(raw.get("tier")) : CaseDef.TIER_FULL;
    if (!CaseDef.isValidTier(tier)) {
      throw new CaseValidationException(
          file + ": tier must be 'smoke' or 'full', was '" + tier + "'");
    }
    return new CaseDef(id, surface, intent, title, spec, given, steps, expect, tier);
  }

  private SpecRef parseSpec(Path file, Object rawSpec) {
    if (rawSpec == null) {
      return null;
    }
    Map<String, Object> spec = asMap(file, "spec", rawSpec, SPEC_KEYS);
    return new SpecRef(stringOrNull(spec.get("doc")), stringOrNull(spec.get("claim")));
  }

  private GivenDef parseGiven(Path file, Object rawGiven) {
    Map<String, Object> given = asMap(file, "given", rawGiven, GIVEN_KEYS);
    String mode = requireString(file, given, "mode").toUpperCase(Locale.ROOT);
    if ("TARGET".equals(mode)) {
      rejectKeys(file, given, "target", "clientArgs");
      return GivenDef.target(
          requireString(file, given, "target"), requireString(file, given, "agentArgs"));
    }
    if ("CLIENT".equals(mode)) {
      rejectKeys(file, given, "client", "target", "agentArgs");
      return GivenDef.client(requireStringList(file, given, "clientArgs"));
    }
    throw new CaseValidationException(
        file + ": given.mode must be 'target' or 'client', was " + mode);
  }

  private void rejectKeys(Path file, Map<String, Object> given, String modeName, String... keys) {
    for (String key : keys) {
      if (given.containsKey(key)) {
        throw new CaseValidationException(
            file + ": '" + key + "' is not allowed in given for mode: " + modeName);
      }
    }
  }

  private ExpectationDef parseExpect(Path file, Object rawExpect) {
    Map<String, Object> expect = asMap(file, "expect", rawExpect, EXPECT_KEYS);
    Object rawExit = expect.get("exitCode");
    Integer exitCode = rawExit == null ? null : Integer.valueOf(rawExit.toString());
    List<String> mentions =
        expect.containsKey("messageMustMention")
            ? requireStringList(file, expect, "messageMustMention")
            : Collections.<String>emptyList();
    return new ExpectationDef(exitCode, mentions);
  }

  private Map<String, Object> asMap(Path file, String what, Object value, Set<String> allowedKeys) {
    if (!(value instanceof Map)) {
      throw new CaseValidationException(file + ": " + what + " must be a mapping");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) value;
    for (String key : map.keySet()) {
      if (!allowedKeys.contains(key)) {
        throw new CaseValidationException(file + ": unknown key '" + key + "' in " + what);
      }
    }
    return map;
  }

  private String requireString(Path file, Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (value == null) {
      throw new CaseValidationException(file + ": '" + key + "' is required");
    }
    return value.toString();
  }

  private List<String> requireStringList(Path file, Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
      throw new CaseValidationException(file + ": '" + key + "' must be a non-empty list");
    }
    List<String> out = new ArrayList<>();
    for (Object item : (List<?>) value) {
      out.add(String.valueOf(item));
    }
    return out;
  }

  private static String stringOrNull(Object value) {
    return value == null ? null : value.toString();
  }
}
