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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompletionTest {

  @Test
  void linesSatisfiedAfterNthLine() {
    Completion c = Completion.lines(2);
    assertFalse(c.onStdout("first"), "not satisfied after 1 line");
    assertTrue(c.onStdout("second"), "satisfied after 2 lines");
  }

  @Test
  void untilContainsNeedsAllMarkersInAnyOrder() {
    Completion c = Completion.untilContains("tag=", "value=");
    assertFalse(c.onStdout("value=42"), "only one marker seen");
    assertFalse(c.onStdout("noise"), "still one marker seen");
    assertTrue(c.onStdout("tag=ext-data-ok"), "both markers now seen");
  }

  @Test
  void untilContainsIsSatisfiedByASingleLineHoldingAllMarkers() {
    Completion c = Completion.untilContains("a", "b");
    assertTrue(c.onStdout("xax by"), "both markers on one line");
  }

  @Test
  void untilMatchesCountsMatchingLinesOnly() {
    Completion c = Completion.untilMatches(Pattern.compile("^event:"), 2);
    assertFalse(c.onStdout("event: one"), "1 match");
    assertFalse(c.onStdout("ignore me"), "still 1 match");
    assertTrue(c.onStdout("event: two"), "2 matches");
  }

  @Test
  void stderrDefaultsToNonReleasing() {
    Completion c = Completion.untilContains("never");
    assertFalse(c.onStderr("anything"), "stderr does not release by default");
  }

  @Test
  void describeIsHumanReadable() {
    assertEquals("2 output line(s)", Completion.lines(2).describe());
    assertTrue(Completion.untilContains("tag=", "value=").describe().contains("tag="));
  }
}
