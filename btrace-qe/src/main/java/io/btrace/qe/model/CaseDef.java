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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CaseDef {
  private static final Set<String> TARGET_STEPS =
      new LinkedHashSet<>(Arrays.asList("launch", "awaitReady", "canary", "shutdown", "awaitExit"));
  private static final Set<String> CLIENT_STEPS =
      new LinkedHashSet<>(Arrays.asList("runClient", "awaitExit"));
  private static final Set<String> ATTACH_STEPS =
      new LinkedHashSet<>(
          Arrays.asList(
              "launch",
              "awaitReady",
              "attach",
              "awaitOutput",
              "awaitClientExit",
              "detach",
              "shutdown",
              "awaitExit"));

  public static final String TIER_SMOKE = "smoke";
  public static final String TIER_FULL = "full";

  private final String id;
  private final String surface;
  private final Intent intent;
  private final String title;
  private final SpecRef spec;
  private final GivenDef given;
  private final List<String> steps;
  private final ExpectationDef expect;
  private final String tier;

  public CaseDef(
      String id,
      String surface,
      Intent intent,
      String title,
      SpecRef spec,
      GivenDef given,
      List<String> steps,
      ExpectationDef expect,
      String tier) {
    this.id = id;
    this.surface = surface;
    this.intent = intent;
    this.title = title;
    this.spec = spec;
    this.given = given;
    this.steps = steps;
    this.expect = expect;
    this.tier = tier == null ? TIER_FULL : tier;
  }

  public static boolean isValidTier(String tier) {
    return TIER_SMOKE.equals(tier) || TIER_FULL.equals(tier);
  }

  public void validate() {
    require(id != null && !id.trim().isEmpty(), "id is required");
    require(surface != null && !surface.trim().isEmpty(), "surface is required");
    require(intent != null, "intent is required");
    require(title != null && !title.trim().isEmpty(), "title is required");
    require(given != null, "given is required");
    require(steps != null && !steps.isEmpty(), "steps must not be empty");
    require(expect != null, "expect is required");
    require(isValidTier(tier), "tier must be 'smoke' or 'full', was '" + tier + "'");
    if (intent == Intent.CONFORMANCE) {
      require(
          spec != null && spec.doc() != null && spec.claim() != null,
          "conformance cases require spec.doc and spec.claim");
    } else {
      require(
          expect.exitCode() == null,
          "expect.exitCode is a documented-behavior claim and is only allowed on conformance cases");
      require(
          expect.clientExitCode() == null,
          "expect.clientExitCode is a documented-behavior claim and is only allowed on conformance"
              + " cases");
    }
    Set<String> allowed = stepsFor(given.mode());
    for (String step : steps) {
      require(allowed.contains(step), "step '" + step + "' is not valid for mode " + given.mode());
    }
    switch (given.mode()) {
      case TARGET:
        require("launch".equals(steps.get(0)), "the first step of a target case must be 'launch'");
        requirePrecedes("awaitReady", Arrays.asList("canary", "shutdown"), "target");
        break;
      case ATTACH:
        require("launch".equals(steps.get(0)), "the first step of an attach case must be 'launch'");
        requirePrecedes("awaitReady", Arrays.asList("attach", "shutdown"), "attach");
        requirePrecedes(
            "attach", Arrays.asList("awaitOutput", "awaitClientExit", "detach"), "attach");
        require(
            !steps.contains("awaitOutput") || !expect.outputMustMatch().isEmpty(),
            "'awaitOutput' needs at least one pattern in expect.outputMustMatch");
        break;
      default:
        require(
            "runClient".equals(steps.get(0)),
            "the first step of a client case must be 'runClient'");
        break;
    }
    if (given.mode() != GivenDef.Mode.CLIENT) {
      validateTarget();
    }
    if (expect.clientExitCode() != null) {
      require(
          given.mode() == GivenDef.Mode.ATTACH,
          "expect.clientExitCode is only allowed in attach mode (it is the attached client's exit"
              + " code)");
      // without the step the client may still be running when the harness reaps it
      require(
          steps.contains("awaitClientExit"),
          "expect.clientExitCode needs an 'awaitClientExit' step, or the client may not have exited");
    }
    require(
        expect.outputMustMatch().isEmpty() || given.mode() == GivenDef.Mode.ATTACH,
        "expect.outputMustMatch is only allowed in attach mode (it matches the client's output)");
    for (String regex : expect.outputMustMatch()) {
      try {
        Pattern.compile(regex);
      } catch (PatternSyntaxException e) {
        require(false, "expect.outputMustMatch entry is not a valid regex: " + e.getMessage());
      }
    }
  }

  private void validateTarget() {
    String target = given.target();
    require(target != null && !target.trim().isEmpty(), "given.target is required");
    if (given.isSourceTarget()) {
      boolean relative = !target.startsWith("/") && !target.contains("\\");
      boolean escapes = false;
      for (String part : target.split("/")) {
        escapes |= "..".equals(part);
      }
      require(
          relative && !escapes,
          "given.target source file must be a path relative to the repository root without '..', was '"
              + target
              + "'");
    } else {
      List<String> all = new ArrayList<>(given.jvmArgs());
      all.addAll(given.clientArgs());
      for (String arg : all) {
        require(
            !arg.contains("${targetDir}"),
            "${targetDir} is only defined when given.target is a source file, found in '"
                + arg
                + "'");
      }
    }
    try {
      Pattern.compile(given.readyRegex());
    } catch (PatternSyntaxException e) {
      require(false, "given.readyRegex is not a valid regex: " + e.getMessage());
    }
  }

  private static Set<String> stepsFor(GivenDef.Mode mode) {
    switch (mode) {
      case TARGET:
        return TARGET_STEPS;
      case ATTACH:
        return ATTACH_STEPS;
      default:
        return CLIENT_STEPS;
    }
  }

  private void requirePrecedes(String first, List<String> dependents, String caseKind) {
    int at = steps.indexOf(first);
    for (String dependent : dependents) {
      int dep = steps.indexOf(dependent);
      require(
          dep < 0 || (at >= 0 && at < dep),
          "'" + first + "' must precede '" + dependent + "' in " + article(caseKind) + " case");
    }
  }

  private static String article(String kind) {
    return (kind.startsWith("a") ? "an " : "a ") + kind;
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

  /** Run tier: {@code smoke} cases form the fast subset; everything else is {@code full}. */
  public String tier() {
    return tier;
  }
}
