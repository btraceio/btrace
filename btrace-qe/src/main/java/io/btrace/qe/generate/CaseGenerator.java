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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Runs all mutator families for one seed and a per-family budget. */
public final class CaseGenerator {
  private static final List<MutatorFamily> FAMILIES =
      Collections.unmodifiableList(
          Arrays.<MutatorFamily>asList(new AgentArgsValuesFamily(), new CliFlagsFamily()));

  /** Reserved prefix of every generated case id; corpus files may not use it. */
  public static final String ID_PREFIX = "gen.";

  private CaseGenerator() {}

  public static boolean isGenerated(CaseDef def) {
    return def.id().startsWith(ID_PREFIX);
  }

  public static List<MutatorFamily> families() {
    return FAMILIES;
  }

  /**
   * Cases for {@code seed}: each family draws from its own {@code Random(seed ^ id.hashCode())}, so
   * adding a family never shifts the cases of another. Ids are {@code gen.<family>.<seed>.<index>}.
   */
  public static List<CaseDef> generate(long seed, int budgetPerFamily) {
    List<CaseDef> all = new ArrayList<>();
    if (budgetPerFamily <= 0) {
      return all;
    }
    for (MutatorFamily family : FAMILIES) {
      List<CaseDef> cases =
          family.generate(new Random(seed ^ family.id().hashCode()), budgetPerFamily);
      for (int index = 0; index < cases.size(); index++) {
        all.add(withId(cases.get(index), ID_PREFIX + family.id() + "." + seed + "." + index));
      }
    }
    return all;
  }

  private static CaseDef withId(CaseDef d, String id) {
    return new CaseDef(
        id,
        d.surface(),
        d.intent(),
        d.title(),
        d.spec(),
        d.given(),
        d.steps(),
        d.expect(),
        d.tier());
  }
}
