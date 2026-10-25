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

public final class Finding {
  private final String caseId;
  private final Severity severity;
  private final String summary;
  private final String detail;

  private Finding(String caseId, Severity severity, String summary, String detail) {
    this.caseId = caseId;
    this.severity = severity;
    this.summary = summary;
    this.detail = detail;
  }

  public static Finding of(String caseId, Severity severity, String summary, String detail) {
    return new Finding(caseId, severity, summary, detail);
  }

  public String caseId() {
    return caseId;
  }

  public Severity severity() {
    return severity;
  }

  public String summary() {
    return summary;
  }

  public String detail() {
    return detail;
  }
}
