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

import java.lang.instrument.Instrumentation;

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import io.btrace.instr.BTraceTransformer;

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
