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
package io.btrace.qe.generate;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.GivenDef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Writes a case in the corpus YAML schema, so a generated finding can be copied into {@code
 * btrace-qe/cases/} as is.
 */
public final class CaseYamlWriter {
  private CaseYamlWriter() {}

  public static void write(CaseDef def, Path file) throws IOException {
    Files.write(file, toYaml(def).getBytes(StandardCharsets.UTF_8));
  }

  public static String toYaml(CaseDef def) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("id", def.id());
    doc.put("surface", def.surface());
    doc.put("intent", def.intent().name().toLowerCase(Locale.ROOT));
    if (!CaseDef.TIER_FULL.equals(def.tier())) {
      doc.put("tier", def.tier());
    }
    doc.put("title", def.title());
    if (def.spec() != null) {
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("doc", def.spec().doc());
      spec.put("claim", def.spec().claim());
      doc.put("spec", spec);
    }
    Map<String, Object> given = new LinkedHashMap<>();
    GivenDef g = def.given();
    given.put("mode", g.mode().name().toLowerCase(Locale.ROOT));
    if (g.mode() != GivenDef.Mode.CLIENT) {
      given.put("target", g.target());
    }
    if (g.mode() == GivenDef.Mode.TARGET) {
      given.put("agentArgs", g.agentArgs());
    } else {
      given.put("clientArgs", g.clientArgs());
    }
    if (!g.jvmArgs().isEmpty()) {
      given.put("jvmArgs", g.jvmArgs());
    }
    if (!GivenDef.DEFAULT_READY_REGEX.equals(g.readyRegex())) {
      given.put("readyRegex", g.readyRegex());
    }
    doc.put("given", given);
    doc.put("steps", def.steps());
    Map<String, Object> expect = new LinkedHashMap<>();
    if (def.expect().exitCode() != null) {
      expect.put("exitCode", def.expect().exitCode());
    }
    if (!def.expect().messageMustMention().isEmpty()) {
      expect.put("messageMustMention", def.expect().messageMustMention());
    }
    if (!def.expect().outputMustMatch().isEmpty()) {
      expect.put("outputMustMatch", def.expect().outputMustMatch());
    }
    doc.put("expect", expect);

    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setAllowUnicode(true);
    options.setSplitLines(false);
    options.setWidth(Integer.MAX_VALUE);
    return new Yaml(options).dump(doc);
  }
}
