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
package io.btrace.qe.report;

import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonReporter {

  public void write(RunMetadata meta, List<CaseVerdict> verdicts, Path outFile) throws IOException {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("seed", meta.seed());
    root.put("gitSha", meta.gitSha());
    root.put("btraceVersion", meta.btraceVersion());
    root.put("jdk", meta.jdk());
    root.put("os", meta.os());
    root.put("cpuCount", meta.cpuCount());
    root.put("commandLine", meta.commandLine());
    root.put("tier", meta.tier());
    List<Object> cases = new ArrayList<>();
    for (CaseVerdict v : verdicts) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("id", v.def().id());
      c.put("surface", v.def().surface());
      c.put("intent", v.def().intent().name());
      c.put("resilience", v.resilience().name());
      c.put("conformance", v.conformance().name());
      c.put("guidance", v.guidance().name());
      c.put("harnessError", v.harnessError());
      List<Object> findings = new ArrayList<>();
      for (Finding f : v.findings()) {
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("severity", f.severity().name());
        fm.put("summary", f.summary());
        fm.put("detail", f.detail());
        fm.put("reproCommand", ReproCommand.forCase(v.def(), meta.timeoutMs(), meta.tier()));
        findings.add(fm);
      }
      c.put("findings", findings);
      cases.add(c);
    }
    root.put("cases", cases);
    Files.createDirectories(outFile.getParent());
    Files.write(outFile, (new JsonWriter().write(root) + "\n").getBytes(StandardCharsets.UTF_8));
  }
}
