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

import io.btrace.core.Args;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Mutates one agent option per case with hostile value lenses. Option names are the public String
 * constants of {@link Args}, sorted so the choice depends only on the seed.
 */
public final class AgentArgsValuesFamily implements MutatorFamily {
  public static final String ID = "agent-args.values";

  /** Options the harness controls, that name files or classpaths, or that must not be fuzzed. */
  private static final Set<String> EXCLUDED =
      new HashSet<>(
          Arrays.asList(
              Args.SCRIPT,
              Args.SCRIPT_DIR,
              Args.PORT,
              Args.NO_SERVER,
              Args.STDOUT,
              Args.SYSTEM_CLASS_PATH,
              Args.BOOT_CLASS_PATH,
              Args.AGENT_JAR,
              Args.CONFIG,
              Args.LIBS,
              Args.AUTH_TOKEN_FILE,
              Args.PROBE_DESC_PATH,
              Args.DUMP_DIR,
              Args.SCRIPT_OUTPUT_FILE,
              Args.SCRIPT_OUTPUT_DIR,
              // help always exits the target; telemetry must never be enabled by a lens
              Args.HELP,
              Args.TELEMETRY));

  private static final String BASE_ARGS =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,"
          + "noServer=false,stdout=true,";

  @Override
  public String id() {
    return ID;
  }

  /** Mutable option names: all {@link Args} string constants minus the excluded ones, sorted. */
  static List<String> optionNames() {
    List<String> names = new ArrayList<>();
    for (Field f : Args.class.getFields()) {
      int m = f.getModifiers();
      if (Modifier.isPublic(m)
          && Modifier.isStatic(m)
          && Modifier.isFinal(m)
          && f.getType() == String.class) {
        try {
          String name = (String) f.get(null);
          if (!EXCLUDED.contains(name)) {
            names.add(name);
          }
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
    }
    Collections.sort(names);
    return names;
  }

  @Override
  public List<CaseDef> generate(Random rnd, int budget) {
    List<String> options = optionNames();
    List<CaseDef> cases = new ArrayList<>();
    for (int i = 0; i < budget; i++) {
      String option = options.get(rnd.nextInt(options.size()));
      int lens = rnd.nextInt(ValueLenses.COUNT);
      String value = ValueLenses.value(lens, rnd);
      cases.add(
          new CaseDef(
              CaseGenerator.ID_PREFIX + ID + "." + i,
              "agent-args",
              Intent.RESILIENCE,
              "Generated: agent option " + option + " with " + ValueLenses.name(lens) + " value",
              null,
              GivenDef.target("SteadyStateTarget", BASE_ARGS + option + "=" + value),
              Arrays.asList("launch", "awaitReady", "canary", "shutdown", "awaitExit"),
              new ExpectationDef(null, Collections.<String>emptyList()),
              CaseDef.TIER_FULL));
    }
    return cases;
  }
}
