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

import io.btrace.qe.generate.CaseGenerator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class YamlCaseLoader {
  private static final Set<String> TOP_KEYS =
      new HashSet<>(
          Arrays.asList(
              "id", "surface", "intent", "title", "spec", "given", "steps", "expect", "tier"));
  private static final Set<String> SPEC_KEYS = new HashSet<>(Arrays.asList("doc", "claim"));
  private static final Set<String> GIVEN_KEYS =
      new HashSet<>(
          Arrays.asList("mode", "target", "agentArgs", "clientArgs", "jvmArgs", "readyRegex"));
  private static final Set<String> EXPECT_KEYS =
      new HashSet<>(Arrays.asList("exitCode", "messageMustMention", "outputMustMatch"));

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
    for (Path file : files) {
      result.add(loadFile(file));
    }
    return result;
  }

  /** Loads one case file; every failure surfaces as a {@link CaseValidationException} naming it. */
  private CaseDef loadFile(Path file) throws IOException {
    // A fresh Yaml per file: Yaml instances are not thread-safe and carry per-document state.
    LoaderOptions options = new LoaderOptions();
    // A repeated key is ambiguous (SnakeYAML would silently keep the last value), so refuse it.
    options.setAllowDuplicateKeys(false);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    Object loaded;
    try (InputStream in = Files.newInputStream(file)) {
      loaded = yaml.load(in);
    } catch (YAMLException e) {
      throw new CaseValidationException(file + ": invalid YAML: " + e.getMessage());
    }
    CaseDef def = parse(file, asMap(file, "document", loaded, TOP_KEYS));
    if (CaseGenerator.isGenerated(def)) {
      throw new CaseValidationException(
          file
              + ": ids starting with '"
              + CaseGenerator.ID_PREFIX
              + "' are reserved for generated cases, was '"
              + def.id()
              + "'");
    }
    try {
      def.validate();
    } catch (CaseValidationException e) {
      // CaseDef messages are keyed by case id; add the file so the author can find it.
      throw new CaseValidationException(file + ": " + e.getMessage());
    }
    return def;
  }

  private CaseDef parse(Path file, Map<String, Object> raw) {
    String id = requireString(file, raw, "id");
    String surface = requireString(file, raw, "surface");
    String intentName = requireString(file, raw, "intent");
    Intent intent;
    try {
      intent = Intent.valueOf(intentName.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new CaseValidationException(
          file
              + ": intent must be one of "
              + Arrays.toString(Intent.values())
              + ", was '"
              + intentName
              + "'");
    }
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
      return withLaunchOptions(
          file,
          given,
          GivenDef.target(
              requireString(file, given, "target"), requireString(file, given, "agentArgs")));
    }
    if ("CLIENT".equals(mode)) {
      rejectKeys(file, given, "client", "target", "agentArgs", "jvmArgs", "readyRegex");
      return GivenDef.client(requireStringList(file, given, "clientArgs"));
    }
    if ("ATTACH".equals(mode)) {
      rejectKeys(file, given, "attach", "agentArgs");
      return withLaunchOptions(
          file,
          given,
          GivenDef.attach(
              requireString(file, given, "target"), requireStringList(file, given, "clientArgs")));
    }
    throw new CaseValidationException(
        file + ": given.mode must be 'target', 'client' or 'attach', was " + mode);
  }

  /** Applies the optional {@code jvmArgs} and {@code readyRegex} of a launched-target mode. */
  private GivenDef withLaunchOptions(Path file, Map<String, Object> given, GivenDef base) {
    GivenDef result = base;
    if (given.containsKey("jvmArgs")) {
      result = result.withJvmArgs(requireStringList(file, given, "jvmArgs"));
    }
    if (given.containsKey("readyRegex")) {
      result = result.withReadyRegex(requireString(file, given, "readyRegex"));
    }
    return result;
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
    Integer exitCode = null;
    if (rawExit != null) {
      try {
        exitCode = Integer.valueOf(rawExit.toString());
      } catch (NumberFormatException e) {
        throw new CaseValidationException(
            file + ": expect.exitCode must be an integer, was '" + rawExit + "'");
      }
    }
    List<String> mentions =
        expect.containsKey("messageMustMention")
            ? requireStringList(file, expect, "messageMustMention")
            : Collections.<String>emptyList();
    List<String> outputMustMatch =
        expect.containsKey("outputMustMatch")
            ? requireStringList(file, expect, "outputMustMatch")
            : Collections.<String>emptyList();
    return new ExpectationDef(exitCode, mentions, outputMustMatch);
  }

  private Map<String, Object> asMap(Path file, String what, Object value, Set<String> allowedKeys) {
    if (!(value instanceof Map)) {
      throw new CaseValidationException(file + ": " + what + " must be a mapping");
    }
    Map<?, ?> raw = (Map<?, ?>) value;
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : raw.entrySet()) {
      if (!(e.getKey() instanceof String)) {
        throw new CaseValidationException(
            file + ": keys in " + what + " must be strings, found '" + e.getKey() + "'");
      }
      String key = (String) e.getKey();
      if (!allowedKeys.contains(key)) {
        throw new CaseValidationException(file + ": unknown key '" + key + "' in " + what);
      }
      map.put(key, e.getValue());
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
