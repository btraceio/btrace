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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.Profiler;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void recordDoesNotWaitForSnapshot() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);
    r.recordEntry("A");
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          r.recordExit("A", 1);
          r.getRecords(false);
        });
  }

  @Test
  void snapshotFailureBeforeStateAcquisitionDoesNotReleaseForeignState() {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);
    r.setTestHook(
        new MethodInvocationRecorder.TestHook() {
          @Override
          public void beforeSnapshotAcquire() {
            throw new IllegalStateException("forced before acquisition");
          }

          @Override
          public void afterSnapshotAcquire() {}
        });
    assertThrows(IllegalStateException.class, () -> r.getRecords(false));

    r.setTestHook(null);
    r.recordEntry("recovered");
    r.recordExit("recovered", 3);
    assertNotNull(find(r.getRecords(false), "recovered"));
  }

  @Test
  void delayedEntryAndExitDoNotWaitForSnapshotAndDrainInFifoOrder() throws Exception {
    MethodInvocationRecorder r = new MethodInvocationRecorder(16);
    CountDownLatch snapshotOwnsState = new CountDownLatch(1);
    CountDownLatch releaseSnapshot = new CountDownLatch(1);
    CountDownLatch delayedCallsReturned = new CountDownLatch(1);
    AtomicReference<Profiler.Record[]> snapshot = new AtomicReference<>();
    r.recordEntry("outer");
    r.setTestHook(
        new MethodInvocationRecorder.TestHook() {
          @Override
          public void beforeSnapshotAcquire() {}

          @Override
          public void afterSnapshotAcquire() {
            snapshotOwnsState.countDown();
            try {
              releaseSnapshot.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          }
        });
    Thread snapshotThread =
        new Thread(() -> snapshot.set(r.getRecords(false)), "issue-888-snapshot");
    snapshotThread.start();
    assertTrue(snapshotOwnsState.await(5, TimeUnit.SECONDS));

    Thread application =
        new Thread(
            () -> {
              r.recordEntry("deferred");
              r.recordExit("deferred", 9);
              delayedCallsReturned.countDown();
            },
            "issue-888-application");
    application.start();
    assertTrue(delayedCallsReturned.await(1, TimeUnit.SECONDS));
    assertEquals(2, r.delayedRecordCountForTest());
    releaseSnapshot.countDown();
    snapshotThread.join(5000L);
    application.join(5000L);
    r.setTestHook(null);

    Profiler.Record deferred = find(snapshot.get(), "deferred");
    assertNotNull(deferred);
    assertEquals(1L, deferred.invocations);
    assertEquals(9L, deferred.wallTime);
  }
}
