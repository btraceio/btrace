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
package io.btrace.qe.oracle;

public final class CheckResult {
  private final CheckOutcome outcome;
  private final String detail;

  private CheckResult(CheckOutcome outcome, String detail) {
    this.outcome = outcome;
    this.detail = detail;
  }

  public static CheckResult of(CheckOutcome outcome, String detail) {
    return new CheckResult(outcome, detail);
  }

  public CheckOutcome outcome() {
    return outcome;
  }

  public String detail() {
    return detail;
  }
}
