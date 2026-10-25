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
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds client command lines from the flag list in {@code btrace-client}'s {@code Main} argument
 * loop, with hostile value lenses, duplicated and reordered flags, always ending in an unused
 * numeric PID and a script that does not exist.
 */
public final class CliFlagsFamily implements MutatorFamily {
  public static final String ID = "cli.flags";

  /** Flags that consume the next token. */
  private static final List<String> VALUE_FLAGS =
      Arrays.asList(
          "-p",
          "-o",
          "-d",
          "-pd",
          "-cp",
          "-classpath",
          "-statsd",
          "-host",
          "-r",
          "-n",
          "--oneliner");

  /** Flags that do not consume a value, plus one that is not a flag at all. */
  private static final List<String> BARE_FLAGS =
      Arrays.asList("-u", "-v", "-lp", "-le", "-x", "--no-such-flag");

  /**
   * A numeric PID skips the JVM-name lookup (which needs jvmstat exports), and the script does not
   * exist, so well-formed lines end in "File not found" instead of attaching to anything.
   */
  static final String UNUSED_PID = "2147483647";

  static final String NO_SUCH_SCRIPT = "qe-no-such-script.java";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<CaseDef> generate(Random rnd, int budget) {
    List<CaseDef> cases = new ArrayList<>();
    for (int i = 0; i < budget; i++) {
      List<List<String>> items = new ArrayList<>();
      int n = 1 + rnd.nextInt(4);
      for (int k = 0; k < n; k++) {
        items.add(item(rnd));
      }
      if (rnd.nextInt(4) == 0) {
        items.add(new ArrayList<>(items.get(rnd.nextInt(items.size()))));
      }
      Collections.shuffle(items, rnd);
      List<String> argv = new ArrayList<>();
      for (List<String> item : items) {
        argv.addAll(item);
      }
      argv.add(UNUSED_PID);
      argv.add(NO_SUCH_SCRIPT);
      cases.add(
          new CaseDef(
              CaseGenerator.ID_PREFIX + ID + "." + i,
              "cli",
              Intent.RESILIENCE,
              "Generated: client with " + items.size() + " mutated flag group(s)",
              null,
              GivenDef.client(argv),
              Arrays.asList("runClient", "awaitExit"),
              new ExpectationDef(null, Collections.<String>emptyList()),
              CaseDef.TIER_FULL));
    }
    return cases;
  }

  private static List<String> item(Random rnd) {
    int total = VALUE_FLAGS.size() + BARE_FLAGS.size();
    int pick = rnd.nextInt(total);
    List<String> item = new ArrayList<>();
    if (pick < VALUE_FLAGS.size()) {
      item.add(VALUE_FLAGS.get(pick));
      item.add(ValueLenses.value(rnd.nextInt(ValueLenses.COUNT), rnd));
    } else {
      item.add(BARE_FLAGS.get(pick - VALUE_FLAGS.size()));
    }
    return item;
  }
}
