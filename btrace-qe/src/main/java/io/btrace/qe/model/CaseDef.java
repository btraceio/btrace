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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CaseDef {
  private static final Set<String> TARGET_STEPS =
      new LinkedHashSet<>(Arrays.asList("launch", "awaitReady", "canary", "shutdown", "awaitExit"));
  private static final Set<String> CLIENT_STEPS =
      new LinkedHashSet<>(Arrays.asList("runClient", "awaitExit"));

  private final String id;
  private final String surface;
  private final Intent intent;
  private final String title;
  private final SpecRef spec;
  private final GivenDef given;
  private final List<String> steps;
  private final ExpectationDef expect;

  public CaseDef(
      String id,
      String surface,
      Intent intent,
      String title,
      SpecRef spec,
      GivenDef given,
      List<String> steps,
      ExpectationDef expect) {
    this.id = id;
    this.surface = surface;
    this.intent = intent;
    this.title = title;
    this.spec = spec;
    this.given = given;
    this.steps = steps;
    this.expect = expect;
  }

  public void validate() {
    require(id != null && !id.trim().isEmpty(), "id is required");
    require(surface != null && !surface.trim().isEmpty(), "surface is required");
    require(intent != null, "intent is required");
    require(title != null && !title.trim().isEmpty(), "title is required");
    require(given != null, "given is required");
    require(steps != null && !steps.isEmpty(), "steps must not be empty");
    require(expect != null, "expect is required");
    if (intent == Intent.CONFORMANCE) {
      require(
          spec != null && spec.doc() != null && spec.claim() != null,
          "conformance cases require spec.doc and spec.claim");
    } else {
      require(
          expect.exitCode() == null,
          "expect.exitCode is a documented-behavior claim and is only allowed on conformance cases");
    }
    Set<String> allowed = given.mode() == GivenDef.Mode.TARGET ? TARGET_STEPS : CLIENT_STEPS;
    for (String step : steps) {
      require(allowed.contains(step), "step '" + step + "' is not valid for mode " + given.mode());
    }
    if (given.mode() == GivenDef.Mode.TARGET) {
      require("launch".equals(steps.get(0)), "the first step of a target case must be 'launch'");
      int ready = steps.indexOf("awaitReady");
      for (String dependent : Arrays.asList("canary", "shutdown")) {
        int at = steps.indexOf(dependent);
        require(
            at < 0 || (ready >= 0 && ready < at),
            "'awaitReady' must precede '" + dependent + "' in a target case");
      }
    } else {
      require(
          "runClient".equals(steps.get(0)), "the first step of a client case must be 'runClient'");
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new CaseValidationException((id != null ? id : "<unknown case>") + ": " + message);
    }
  }

  public String id() {
    return id;
  }

  public String surface() {
    return surface;
  }

  public Intent intent() {
    return intent;
  }

  public String title() {
    return title;
  }

  public SpecRef spec() {
    return spec;
  }

  public GivenDef given() {
    return given;
  }

  public List<String> steps() {
    return steps;
  }

  public ExpectationDef expect() {
    return expect;
  }
}
