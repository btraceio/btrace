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
package tests.harness;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Decides when a BTrace client's output stream is "done" for a test.
 *
 * <p>Replaces the historical {@code int checkLines} line-count, which conflated framework bootstrap
 * output with real probe output and therefore raced whenever the framework line count shifted
 * between environments (log level, JDK-version warnings, debug flags). A {@code Completion} lets a
 * test wait for the exact content it is about to assert on.
 */
public interface Completion {

  /** Offer one stdout line. Returns {@code true} once the awaited condition is satisfied. */
  boolean onStdout(String line);

  /** Offer one stderr line. Returns {@code true} to release the wait early. Default: never. */
  default boolean onStderr(String line) {
    return false;
  }

  /** Human-readable description of what this condition waits for (used in timeout diagnostics). */
  String describe();

  /** Backward-compatible completion: satisfied after {@code n} offered stdout lines. */
  static Completion lines(final int n) {
    return new Completion() {
      private int seen = 0;

      @Override
      public boolean onStdout(String line) {
        return ++seen >= n;
      }

      @Override
      public String describe() {
        return n + " output line(s)";
      }
    };
  }

  /** Satisfied once every marker substring has appeared across stdout, in any order. */
  static Completion untilContains(final String... markers) {
    return new Completion() {
      private final boolean[] found = new boolean[markers.length];
      private int remaining = markers.length;

      @Override
      public boolean onStdout(String line) {
        for (int i = 0; i < markers.length; i++) {
          if (!found[i] && line.contains(markers[i])) {
            found[i] = true;
            remaining--;
          }
        }
        return remaining <= 0;
      }

      @Override
      public String describe() {
        return "all of " + Arrays.toString(markers);
      }
    };
  }

  /** Satisfied once {@code n} stdout lines match {@code pattern}. */
  static Completion untilMatches(final Pattern pattern, final int n) {
    return new Completion() {
      private int matches = 0;

      @Override
      public boolean onStdout(String line) {
        if (pattern.matcher(line).find()) {
          matches++;
        }
        return matches >= n;
      }

      @Override
      public String describe() {
        return n + " line(s) matching /" + pattern.pattern() + "/";
      }
    };
  }
}
