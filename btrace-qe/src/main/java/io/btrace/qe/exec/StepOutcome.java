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
package io.btrace.qe.exec;

public final class StepOutcome {
  private final String step;
  private final boolean success;
  private final long elapsedMs;
  private final String error;

  private StepOutcome(String step, boolean success, long elapsedMs, String error) {
    this.step = step;
    this.success = success;
    this.elapsedMs = elapsedMs;
    this.error = error;
  }

  public static StepOutcome ok(String step, long elapsedMs) {
    return new StepOutcome(step, true, elapsedMs, null);
  }

  public static StepOutcome failed(String step, long elapsedMs, String error) {
    return new StepOutcome(step, false, elapsedMs, error);
  }

  public String step() {
    return step;
  }

  public boolean success() {
    return success;
  }

  public long elapsedMs() {
    return elapsedMs;
  }

  public String error() {
    return error;
  }
}
