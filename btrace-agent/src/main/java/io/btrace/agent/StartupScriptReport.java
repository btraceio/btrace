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

import io.btrace.instr.Constants;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Reports startup probes the agent could not load. {@code scriptdir=} may hold other files
 * (sources, notes) that are skipped quietly, but a probe named in {@code script=} was asked for
 * explicitly: it used to be skipped with no message at any log level, so a typo in a launch script
 * left the operator believing the probe was running.
 */
final class StartupScriptReport {
  private StartupScriptReport() {}

  /** The probes named in a {@code script=} value ({@code :}-separated), or none. */
  static Set<String> namedScripts(String scriptArg) {
    if (scriptArg == null) {
      return Collections.emptySet();
    }
    Set<String> named = new HashSet<>();
    StringTokenizer tokenizer = new StringTokenizer(scriptArg, ":");
    while (tokenizer.hasMoreTokens()) {
      named.add(tokenizer.nextToken());
    }
    return named;
  }

  /** Why a probe named in {@code script=} did not load, phrased for the operator. */
  static String unloadableMessage(String script) {
    String prefix = "BTrace startup script " + script + " was not loaded: ";
    if (script.endsWith(".java")) {
      return prefix
          + "script= takes a compiled probe; compile it with btracec and pass the .class file";
    }
    if (!new File(script).exists()
        && ClassLoader.getSystemResource(Constants.EMBEDDED_BTRACE_SECTION_HEADER + script)
            == null) {
      return prefix
          + "no such file (relative paths resolve against "
          + new File("").getAbsolutePath()
          + ")";
    }
    return prefix + "see the preceding messages for the reason";
  }
}
