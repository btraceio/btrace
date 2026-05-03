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
package io.btrace.compiler.oneliner;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import io.btrace.compiler.oneliner.OnelinerAST.*;

/** Semantic validator for BTrace oneliner AST */
public class OnelinerValidator {

  /**
   * Validate a parsed oneliner AST
   *
   * @throws OnelinerException if validation fails
   */
  public static void validate(OnelinerNode node, String input) {
    ProbeClause probe = node.probe;

    // Validate probe spec
    validateProbeSpec(probe.probeSpec, input);

    // Validate filter for location
    if (probe.filter != null) {
      validateFilter(probe.filter, probe.probeSpec.location, input);
    }

    // Validate actions for location
    validateActions(probe.actionBlock, probe.probeSpec.location, input);
  }

  private static void validateProbeSpec(ProbeSpec spec, String input) {
    // Validate class pattern is not empty
    if (spec.classPattern == null || spec.classPattern.trim().isEmpty()) {
      throw new OnelinerException("Class pattern cannot be empty", input, 0);
    }

    // Validate method pattern is not empty
    if (spec.methodPattern == null || spec.methodPattern.trim().isEmpty()) {
      throw new OnelinerException("Method pattern cannot be empty", input, 0);
    }

    // Validate regex patterns
    validateRegexPattern(spec.classPattern, "class pattern", input);
    validateRegexPattern(spec.methodPattern, "method pattern", input);
  }

  private static void validateRegexPattern(String pattern, String patternName, String input) {
    if (pattern.startsWith("/") && pattern.endsWith("/")) {
      String regex = pattern.substring(1, pattern.length() - 1);
      try {
        Pattern.compile(regex);
      } catch (PatternSyntaxException e) {
        throw new OnelinerException(
            "Invalid regex in " + patternName + ": " + e.getMessage(), input, 0);
      }
    }
  }

  private static void validateFilter(Filter filter, Location location, String input) {
    if (filter.type == FilterType.DURATION) {
      // Duration filter only valid for RETURN and ERROR locations
      if (location != Location.RETURN && location != Location.ERROR) {
        throw new OnelinerException(
            "'duration' filter requires @return or @error location (found: @"
                + location.name().toLowerCase()
                + ")",
            input,
            0);
      }

      // Validate duration value is positive
      if (filter.value instanceof Integer && (Integer) filter.value <= 0) {
        throw new OnelinerException("Duration value must be positive", input, 0);
      }
    }

    if (filter.type == FilterType.ARG_COMPARE) {
      ArgFilter argFilter = (ArgFilter) filter;
      // Argument index validation already done in AST construction
      if (argFilter.argIndex < 0) {
        throw new OnelinerException("Argument index must be non-negative", input, 0);
      }
    }
  }

  private static void validateActions(ActionBlock actionBlock, Location location, String input) {
    Set<String> seenActions = new HashSet<>();

    for (Action action : actionBlock.actions) {
      if (action instanceof PrintAction) {
        validatePrintAction((PrintAction) action, location, input);
      } else if (action instanceof CountAction) {
        // Count action is valid for all locations
        if (seenActions.contains("count")) {
          throw new OnelinerException("Duplicate 'count' action in action block", input, 0);
        }
        seenActions.add("count");
      } else if (action instanceof TimeAction) {
        // Time action requires RETURN or ERROR location
        if (location != Location.RETURN && location != Location.ERROR) {
          throw new OnelinerException(
              "'time' action requires @return or @error location (found: @"
                  + location.name().toLowerCase()
                  + ")",
              input,
              0);
        }
        if (seenActions.contains("time")) {
          throw new OnelinerException("Duplicate 'time' action in action block", input, 0);
        }
        seenActions.add("time");
      } else if (action instanceof StackAction) {
        StackAction stackAction = (StackAction) action;
        // Stack depth validation already done in AST construction
        if (stackAction.depth != null && stackAction.depth <= 0) {
          throw new OnelinerException(
              "Stack depth must be positive (got: " + stackAction.depth + ")", input, 0);
        }
      }
    }
  }

  private static void validatePrintAction(PrintAction action, Location location, String input) {
    for (String arg : action.args) {
      switch (arg) {
        case "duration":
          if (location != Location.RETURN && location != Location.ERROR) {
            throw new OnelinerException(
                "'duration' identifier only available with @return or @error location (found: @"
                    + location.name().toLowerCase()
                    + ")",
                input,
                0);
          }
          break;

        case "return":
          if (location != Location.RETURN) {
            throw new OnelinerException(
                "'return' identifier only available with @return location (found: @"
                    + location.name().toLowerCase()
                    + ")",
                input,
                0);
          }
          break;

        case "method":
        case "args":
        case "self":
        case "class":
          // These are valid for all locations
          break;

        default:
          throw new OnelinerException("Unknown print identifier: '" + arg + "'", input, 0);
      }
    }
  }
}
