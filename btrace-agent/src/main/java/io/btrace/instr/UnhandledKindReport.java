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
package io.btrace.instr;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.annotations.Kind;
import io.btrace.core.comm.MessageCommand;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports a handler whose probe {@link Kind} an instrumentation backend has no code for. Both
 * backends handle every kind today ({@code scripts/guardrails/check_kind_dispatch.py}), so this
 * runs only when a kind is added to one backend and not the other: exactly when skipping the
 * handler quietly would hide the bug. Shared by the ASM backend ({@link Instrumentor}) and the
 * ClassFile API backend so both say the same thing.
 *
 * <p>The handler is left out and the class is otherwise instrumented as usual. Like a failed
 * transform, the report goes to the target JVM's log at warn and to the client of the probe, once
 * per handler; nothing is thrown into the thread that loads the class.
 */
final class UnhandledKindReport {
  private static final Logger log = LoggerFactory.getLogger(UnhandledKindReport.class);
  private static final Set<OnMethod> REPORTED =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

  private UnhandledKindReport() {}

  static String message(Kind kind, String handler, String className, String backend) {
    return "Handler "
        + handler
        + " was not applied in "
        + className
        + ": the "
        + backend
        + " instrumentation backend does not handle probe kind "
        + kind
        + " (reported once per handler)";
  }

  /**
   * @param className the class being instrumented, internal or binary name
   * @param backend the name of the backend that has no code for the kind
   */
  static void report(BTraceProbe probe, OnMethod om, String className, String backend) {
    try {
      if (!REPORTED.add(om)) {
        return;
      }
      String text =
          message(
              om.getLocation().getValue(),
              om.getTargetName(),
              className.replace('/', '.'),
              backend);
      log.warn(text);
      BTraceRuntime.Impl rt = probe != null ? probe.getRuntime() : null;
      if (rt != null) {
        rt.sendCommand(new MessageCommand("[BTRACE WARN] " + text));
      }
    } catch (Throwable t) {
      log.warn("Failed to report a handler with an unhandled probe kind in {}", className, t);
    }
  }
}
