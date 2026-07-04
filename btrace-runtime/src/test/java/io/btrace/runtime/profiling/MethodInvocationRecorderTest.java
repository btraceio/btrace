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
package io.btrace.runtime.profiling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.btrace.core.Profiler;
import org.junit.jupiter.api.Test;

/** Regression tests for {@link MethodInvocationRecorder} timing, reset, and recursion handling. */
class MethodInvocationRecorderTest {

  private static Profiler.Record find(Profiler.Record[] recs, String name) {
    for (Profiler.Record r : recs) {
      if (r != null && name.equals(r.blockName)) {
        return r;
      }
    }
    return null;
  }

  /** Direct recursion (A -> A) must not double-count wall time for the recursive block. */
  @Test
  void directRecursionDoesNotDoubleCountWallTime() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);
    r.recordEntry("A");
    r.recordEntry("A");
    r.recordExit("A", 5); // inner exit: A is an ancestor -> wall time must be zeroed
    r.recordExit("A", 20); // outer exit: no A ancestor -> wall time counts

    Profiler.Record a = find(r.getRecords(false), "A");
    assertNotNull(a, "A must be recorded");
    assertEquals(20L, a.wallTime, "recursive inner call must not add to aggregated wall time");
    assertEquals(2L, a.invocations, "both invocations counted");
  }

  /**
   * Resetting while frames are still on the stack must not corrupt the recorder: the still-live
   * frames must survive and their subsequent exits must not throw.
   */
  @Test
  void resetWhileFramesLiveKeepsStackConsistent() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);
    r.recordEntry("Outer");
    r.recordEntry("Inner");

    // snapshot + reset taken while Outer and Inner are still executing
    assertDoesNotThrow(() -> r.getRecords(true));

    assertDoesNotThrow(
        () -> {
          r.recordExit("Inner", 7);
          r.recordExit("Outer", 30);
        },
        "exits of frames that were live at reset time must not NPE");

    Profiler.Record[] after = r.getRecords(false);
    assertNotNull(find(after, "Outer"), "Outer survives reset and completes");
    assertNotNull(find(after, "Inner"), "Inner survives reset and completes");
  }

  /** {@code getRecords(true)} must reset, so consecutive intervals report deltas, not totals. */
  @Test
  void snapshotAndResetReportsPerIntervalDeltas() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);

    r.recordEntry("M");
    r.recordExit("M", 10);
    Profiler.Record first = find(r.getRecords(true), "M");
    assertNotNull(first);
    assertEquals(1L, first.invocations);

    r.recordEntry("M");
    r.recordExit("M", 4);
    Profiler.Record second = find(r.getRecords(true), "M");
    assertNotNull(second);
    assertEquals(1L, second.invocations, "second interval must not include the first interval");
    assertEquals(4L, second.wallTime, "second interval reports only its own wall time");
  }

  /** Without reset, measurements accumulate across snapshots. */
  @Test
  void snapshotWithoutResetAccumulates() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);

    r.recordEntry("X");
    r.recordExit("X", 3);
    r.getRecords(false);

    r.recordEntry("X");
    r.recordExit("X", 5);
    Profiler.Record x = find(r.getRecords(false), "X");
    assertNotNull(x);
    assertEquals(2L, x.invocations, "invocations accumulate without reset");
  }
}
