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

import io.btrace.qe.exec.QeEnvironment;
import io.btrace.qe.model.CaseDef;

public final class ReproCommand {
  private ReproCommand() {}

  /**
   * The command that re-runs one case; a non-default step timeout and a set tier are part of it.
   */
  public static String forCase(CaseDef def, long timeoutMs, String tier) {
    return "./gradlew :btrace-qe:qe -Pqe.case="
        + def.id()
        + tierFlag(tier)
        + timeoutFlag(timeoutMs);
  }

  /** {@code " -Pqe.tier=<tier>"} when a tier was set, else empty. */
  public static String tierFlag(String tier) {
    return tier != null && !tier.isEmpty() ? " -Pqe.tier=" + tier : "";
  }

  /** {@code " -Pqe.timeout=<ms>"} when the timeout differs from the default, else empty. */
  public static String timeoutFlag(long timeoutMs) {
    return timeoutMs != QeEnvironment.DEFAULT_TIMEOUT_MS ? " -Pqe.timeout=" + timeoutMs : "";
  }
}
