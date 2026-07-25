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

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import io.btrace.instr.BTraceTransformer;
import java.lang.instrument.Instrumentation;

/**
 * Client-context data class
 *
 * @author Jaroslav Bachorik
 */
class ClientContext {
  private final Instrumentation instr;
  private final BTraceTransformer transformer;
  private final ArgsMap args;
  private final SharedSettings settings;

  ClientContext(
      Instrumentation instr, BTraceTransformer transformer, ArgsMap args, SharedSettings settings) {
    // A client's SET_PARAMS command mutates these settings in place, so a context must never be
    // built on the agent-wide instance: that would let one client's debug/dumpDir/outputFile,
    // granted permissions and non-downgradable `trusted` flag leak into every other client and
    // into the global transformer. Seed a per-client copy with Main#newClientSettings instead.
    if (settings == SharedSettings.GLOBAL) {
      throw new IllegalArgumentException(
          "ClientContext must not share SharedSettings.GLOBAL; use Main#newClientSettings(..)"
              + " to seed an isolated per-client copy");
    }
    this.instr = instr;
    this.transformer = transformer;
    this.args = args;
    this.settings = settings;
  }

  Instrumentation getInstr() {
    return instr;
  }

  BTraceTransformer getTransformer() {
    return transformer;
  }

  SharedSettings getSettings() {
    return settings;
  }

  ArgsMap getArguments() {
    return args;
  }
}
