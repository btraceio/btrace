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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CaseRegistry {
  private final List<CaseDef> cases;

  private CaseRegistry(List<CaseDef> cases) {
    this.cases = cases;
  }

  public static CaseRegistry of(List<CaseDef> cases) {
    List<CaseDef> sorted = new ArrayList<>(cases);
    sorted.sort(Comparator.comparing(CaseDef::id));
    Set<String> seen = new HashSet<>();
    for (CaseDef def : sorted) {
      if (!seen.add(def.id())) {
        throw new CaseValidationException("duplicate case id: " + def.id());
      }
    }
    return new CaseRegistry(sorted);
  }

  public List<CaseDef> all() {
    return Collections.unmodifiableList(cases);
  }

  public List<CaseDef> filter(String surfaceCsv, String caseId) {
    return cases.stream()
        .filter(c -> caseId == null || caseId.isEmpty() || c.id().equals(caseId))
        .filter(
            c ->
                surfaceCsv == null
                    || surfaceCsv.isEmpty()
                    || matchesSurface(c.surface(), surfaceCsv))
        .collect(Collectors.toList());
  }

  private static boolean matchesSurface(String surface, String csv) {
    for (String want : csv.split(",")) {
      if (surface.equals(want.trim())) {
        return true;
      }
    }
    return false;
  }
}
