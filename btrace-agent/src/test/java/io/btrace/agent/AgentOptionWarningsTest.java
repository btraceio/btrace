/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.Args;
import io.btrace.core.ArgsMap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class AgentOptionWarningsTest {

  @Test
  void typosOfOptionsAreReportedWithTheOptionTheyResemble() {
    assertSuggests("scritp", Args.SCRIPT); // swapped letters
    assertSuggests("debgu", Args.DEBUG);
    assertSuggests("scriptOutputDri", Args.SCRIPT_OUTPUT_DIR);
    assertSuggests("noServr", Args.NO_SERVER); // letter missing
    assertSuggests("trustedd", Args.TRUSTED); // letter added
    assertSuggests("probex", Args.PROBES); // letter replaced
    assertSuggests("scriptDir", Args.SCRIPT_DIR); // case only, the option is "scriptdir"
    assertSuggests("PORT", Args.PORT); // case only, short names too
  }

  @Test
  void theWarningNamesTheArgumentAndWhatHappensToIt() {
    String warning = AgentOptionWarnings.misspelledOptionWarning("debgu");

    assertTrue(warning.startsWith("Unknown agent option 'debgu'"), warning);
    assertTrue(warning.contains("did you mean 'debug'?"), warning);
    assertTrue(warning.contains("passed on to scripts"), warning);
  }

  @Test
  void everyOptionIsKnown() {
    for (String option : AgentOptionWarnings.knownOptions()) {
      assertNull(AgentOptionWarnings.misspelledOptionWarning(option), option);
    }
  }

  @Test
  void removedOptionsAreOrdinaryUnknownArguments() {
    // libs is below the minimum length and agentJar is not close to any live option, so neither
    // is mistaken for a misspelling; both are passed on to scripts like any other argument.
    assertFalse(AgentOptionWarnings.knownOptions().contains("libs"));
    assertFalse(AgentOptionWarnings.knownOptions().contains("agentJar"));
    assertNull(AgentOptionWarnings.misspelledOptionWarning("libs"));
    assertNull(AgentOptionWarnings.misspelledOptionWarning("agentJar"));
  }

  @Test
  void knownOptionsComeFromTheArgsConstants() {
    List<String> known = AgentOptionWarnings.knownOptions();

    assertTrue(known.contains(Args.SCRIPT_OUTPUT_DIR));
    assertTrue(known.contains(Args.OUTPUT));
    assertEquals(Args.class.getFields().length, known.size());
  }

  @Test
  void documentedScriptAndExtensionArgumentsAreNotReported() {
    // script arguments from the docs, extension settings, system properties, bare list tokens
    String[] legal = {
      "level", "filterValue", "arg", "arg1", "otel.endpoint", "otel.service.name",
      "$btrace.system.appendJar", "$debug", "THREADS", "", "deps", "sort"
    };
    for (String name : legal) {
      assertNull(AgentOptionWarnings.misspelledOptionWarning(name), name);
    }
    // a name shorter than five letters is only reported when it differs from an option by case
    assertNull(AgentOptionWarnings.misspelledOptionWarning("prot"));
    assertNull(AgentOptionWarnings.misspelledOptionWarning("lib"));
  }

  @Test
  void aScriptArgumentOneEditFromAnOptionIsStillReported() {
    // The accepted cost of the rule: a script argument named like this gets a warning it does not
    // need. The warning says the argument still reaches the scripts.
    assertSuggests("ports", Args.PORT);
  }

  @Test
  void namesFarFromEveryOptionAreNotReported() {
    assertNull(AgentOptionWarnings.misspelledOptionWarning("statsdPort"));
    assertNull(AgentOptionWarnings.misspelledOptionWarning("threshold"));
  }

  @Test
  void eachMisspelledArgumentIsLoggedOnceAndTheOthersNotAtAll() {
    ArgsMap args = new ArgsMap();
    args.put("script", "Foo.class");
    args.put("scritp", "Foo.class");
    args.put("level", "1");
    args.put("debgu", "true");
    List<String> warnings = new ArrayList<>();

    AgentOptionWarnings.misspelledOptions(args, recordingLogger(warnings));

    assertEquals(2, warnings.size(), warnings.toString());
    assertTrue(warnings.get(0).contains("'scritp'"), warnings.toString());
    assertTrue(warnings.get(1).contains("'debgu'"), warnings.toString());
  }

  @Test
  void oneEditMeansOneInsertionDeletionReplacementOrAdjacentSwap() {
    assertTrue(AgentOptionWarnings.withinOneEdit("script", "script"));
    assertTrue(AgentOptionWarnings.withinOneEdit("scritp", "script"));
    assertTrue(AgentOptionWarnings.withinOneEdit("scrip", "script"));
    assertTrue(AgentOptionWarnings.withinOneEdit("scripts", "script"));
    assertTrue(AgentOptionWarnings.withinOneEdit("scropt", "script"));
    assertFalse(AgentOptionWarnings.withinOneEdit("sctipr", "script"));
    assertFalse(AgentOptionWarnings.withinOneEdit("scr", "script"));
    assertFalse(AgentOptionWarnings.withinOneEdit("tpircs", "script"));
  }

  private static void assertSuggests(String name, String option) {
    String warning = AgentOptionWarnings.misspelledOptionWarning(name);
    assertNotNull(warning, name);
    assertTrue(warning.contains("did you mean '" + option + "'"), warning);
  }

  /** A logger that records the text of each warn(String) call and ignores everything else. */
  private static Logger recordingLogger(List<String> warnings) {
    return (Logger)
        Proxy.newProxyInstance(
            Logger.class.getClassLoader(),
            new Class<?>[] {Logger.class},
            (proxy, method, a) -> {
              if ("warn".equals(method.getName()) && a != null && a.length == 1) {
                warnings.add(String.valueOf(a[0]));
              }
              return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });
  }
}
