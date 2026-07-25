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
package io.btrace.runtime.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.btrace.core.Profiler;
import org.junit.jupiter.api.Test;

class MethodInvocationProfilerTest {
  @Test
  void snapshotAndResetPruneClearedRecordersWithoutDiscardingLiveData() {
    MethodInvocationProfiler profiler = new MethodInvocationProfiler(8);
    profiler.recordEntry("live");
    profiler.recordExit("live", 4);
    assertEquals(1, profiler.recorderReferenceCountForTest());

    profiler.addClearedRecorderForTest();
    assertEquals(2, profiler.recorderReferenceCountForTest());
    Profiler.Snapshot snapshot = profiler.snapshot(false);
    assertEquals(1, profiler.recorderReferenceCountForTest());
    assertNotNull(find(snapshot.total, "live"));

    profiler.addClearedRecorderForTest();
    assertEquals(2, profiler.recorderReferenceCountForTest());
    profiler.reset();
    assertEquals(1, profiler.recorderReferenceCountForTest());
  }

  private static Profiler.Record find(Profiler.Record[] records, String name) {
    for (Profiler.Record record : records) {
      if (record != null && name.equals(record.blockName)) {
        return record;
      }
    }
    return null;
  }
}
