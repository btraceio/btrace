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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProbeBudgetsTest {

  @BeforeEach
  @AfterEach
  void reset() {
    ProbeBudgets.resetForTesting();
  }

  @Test
  void counterIsPerProbe() {
    LongAdder a = ProbeBudgets.counter("test/budgets/A");
    LongAdder b = ProbeBudgets.counter("test/budgets/B");
    a.increment();
    a.increment();
    b.increment();

    assertEquals(2, ProbeBudgets.counter("test/budgets/A").sum());
    assertEquals(1, ProbeBudgets.counter("test/budgets/B").sum());
    assertSame(a, ProbeBudgets.counter("test/budgets/A"));
  }

  @Test
  void forgetClearsCounterAndTrippedMark() {
    String name = "test/budgets/Forgotten";
    ProbeBudgets.counter(name).add(7);
    ProbeBudgets.markTripped(name);
    assertTrue(ProbeBudgets.isTripped(name));

    ProbeBudgets.forget(name);

    assertFalse(ProbeBudgets.isTripped(name));
    assertEquals(0, ProbeBudgets.counter(name).sum());
  }

  @Test
  void readingHitsDoesNotCreateACounter() {
    assertEquals(0, ProbeBudgets.hits("test/budget/NeverCounted"));
    ProbeBudgets.counter("test/budget/Counted").add(3);
    assertEquals(3, ProbeBudgets.hits("test/budget/Counted"));
    ProbeBudgets.forget("test/budget/Counted");
    assertEquals(0, ProbeBudgets.hits("test/budget/Counted"));
    // Had hits() created a counter, this fresh one would not be the first for the name.
    ProbeBudgets.counter("test/budget/NeverCounted").increment();
    assertEquals(1, ProbeBudgets.hits("test/budget/NeverCounted"));
  }

  @Test
  void forgetClearsTheExemptMark() {
    ProbeBudgets.markExempt("test/budget/Exempt");
    assertTrue(ProbeBudgets.isExempt("test/budget/Exempt"));
    ProbeBudgets.forget("test/budget/Exempt");
    assertFalse(ProbeBudgets.isExempt("test/budget/Exempt"));
  }
}
