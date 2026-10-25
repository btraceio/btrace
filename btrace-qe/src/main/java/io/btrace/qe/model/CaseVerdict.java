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
package io.btrace.qe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaseVerdict {
  private final CaseDef def;
  private final VerdictValue resilience;
  private final VerdictValue conformance;
  private final VerdictValue guidance;
  private final List<Finding> findings;
  private final String harnessError;

  public CaseVerdict(
      CaseDef def,
      VerdictValue resilience,
      VerdictValue conformance,
      VerdictValue guidance,
      List<Finding> findings) {
    this(def, resilience, conformance, guidance, findings, null);
  }

  public CaseVerdict(
      CaseDef def,
      VerdictValue resilience,
      VerdictValue conformance,
      VerdictValue guidance,
      List<Finding> findings,
      String harnessError) {
    this.def = def;
    this.resilience = resilience;
    this.conformance = conformance;
    this.guidance = guidance;
    this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
    this.harnessError = harnessError;
  }

  public CaseDef def() {
    return def;
  }

  public VerdictValue resilience() {
    return resilience;
  }

  public VerdictValue conformance() {
    return conformance;
  }

  public VerdictValue guidance() {
    return guidance;
  }

  public List<Finding> findings() {
    return findings;
  }

  /** Why the harness could not judge this case, or {@code null} when it was judged. */
  public String harnessError() {
    return harnessError;
  }
}
