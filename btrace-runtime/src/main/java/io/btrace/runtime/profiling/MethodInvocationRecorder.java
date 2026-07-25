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

import io.btrace.core.Profiler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * An invocation recorder class. All the invocations must be coming from the same thread (eg. by
 * making a MethodInvocationRecorder instance thread local).
 *
 * <p>The only time multithreaded access must be resolved is when a snapshot of the measured data is
 * being externally requested or the recorder is to be reset.
 *
 * <p>For this an atomic state variable is introduced to prevent simultaneous invocation processing
 * and generating snapshot/resetting.
 *
 * @author Jaroslav Bachorik
 */
class MethodInvocationRecorder {

  private final int defaultBufferSize;
  private final Map<String, Integer> indexMap = new HashMap<>();
  // 0 - available; 1 - processing invocation; 2 - generating snapshot; 3 - resetting
  private final AtomicInteger writerStatus = new AtomicInteger(0);
  private final ConcurrentLinkedQueue<DelayedRecord> delayedRecords = new ConcurrentLinkedQueue<>();
  private volatile TestHook testHook;
  private int stackSize = 200;
  private int stackPtr = -1;
  private int stackBndr = 150;
  private Profiler.Record[] stackArr = new Profiler.Record[stackSize];
  private int measuredSize = 0;
  private int measuredPtr = 0;
  private Profiler.Record[] measured = new Profiler.Record[0];
  private long carryOver = 0L;
  private volatile int lastIndex = 0;

  public MethodInvocationRecorder(int expectedBlockCnt) {
    defaultBufferSize = expectedBlockCnt << 8;
    measuredSize = defaultBufferSize;
    measured = new Profiler.Record[measuredSize];
  }

  void recordEntry(String blockName) {
    record(new DelayedRecord(blockName, -1L));
  }

  private void processEntry(String blockName) {
    Profiler.Record r = new Profiler.Record(blockName);
    addMeasured(r);
    push(r);
    carryOver = 0L; // clear the carryOver; not 2 subsequent calls to recordExit
  }

  void recordExit(String blockName, long duration) {
    record(new DelayedRecord(blockName, duration));
  }

  private void record(DelayedRecord record) {
    if (!writerStatus.compareAndSet(0, 3)) {
      delayedRecords.offer(record);
      return;
    }
    try {
      drainDelayedRecords();
      writerStatus.set(1);
      process(record);
    } finally {
      writerStatus.set(0);
    }
  }

  private void processExit(String blockName, long duration) {
    Profiler.Record r = pop();
    if (r == null) {
      r = new Profiler.Record(blockName);
      addMeasured(r);
    }
    r.wallTime = duration;
    r.selfTime += duration - carryOver;
    // After pop() the remaining live frames occupy indices 0..stackPtr (inclusive);
    // the immediate parent is at stackPtr. Scanning only 0..stackPtr-1 would miss a
    // directly-recursive parent and fail to zero the (double-counted) wall time.
    for (int i = 0; i <= stackPtr; i++) {
      if (stackArr[i] != null && stackArr[i].blockName.equals(blockName)) {
        r.wallTime = 0;
        break;
      }
    }
    r.selfTimeMin = r.selfTimeMax = r.selfTime;
    r.wallTimeMin = r.wallTimeMax = r.wallTime;
    Profiler.Record parent = peek();
    if (parent != null) {
      parent.selfTime -= duration;
    } else {
      carryOver = duration;
    }
  }

  private void drainDelayedRecords() {
    DelayedRecord record;
    while ((record = delayedRecords.poll()) != null) {
      process(record);
    }
  }

  private void process(DelayedRecord record) {
    if (record.duration == -1L) {
      processEntry(record.blockName);
    } else {
      processExit(record.blockName, record.duration);
    }
  }

  Profiler.Record[] getRecords(boolean reset) {
    Profiler.Record[] recs = null;
    boolean acquired = false;
    try {
      TestHook hook = testHook;
      if (hook != null) {
        hook.beforeSnapshotAcquire();
      }
      while (!writerStatus.compareAndSet(0, 2)) {
        LockSupport.parkNanos(this, 600);
      }
      acquired = true;
      if (hook != null) {
        hook.afterSnapshotAcquire();
      }
      drainDelayedRecords();
      compactMeasured();

      recs = new Profiler.Record[lastIndex];
      // copy and detach the record array
      for (int i = 0; i < recs.length; i++) {
        Profiler.Record r = measured[i];
        if (r != null) {
          recs[i] = r.duplicate();
        } else {
          System.err.println("Unexpected NULL record at position " + i + "; ignoring");
        }
      }

      // Honor the atomic snapshot+reset contract: the snapshot above and the reset below
      // happen under the same exclusive writer state, so no invocation can be recorded in
      // between and lost. Previously the `reset` flag was ignored, so snapshotAndReset()
      // silently behaved like a plain snapshot() and reported cumulative totals forever.
      if (reset) {
        resetState();
      }

      return recs;
    } finally {
      if (acquired) {
        writerStatus.compareAndSet(2, 0);
      }
    }
  }

  private void push(Profiler.Record r) {
    if (stackPtr > stackBndr) {
      stackSize = (stackSize * 3) >> 1;
      stackBndr = (stackBndr * 3) >> 1;
      Profiler.Record[] newStack = new Profiler.Record[stackSize];
      System.arraycopy(stackArr, 0, newStack, 0, stackPtr + 1);
      stackArr = newStack;
    }
    stackArr[++stackPtr] = r;
    r.onStack = true;
  }

  private Profiler.Record pop() {
    Profiler.Record r = stackPtr > -1 ? stackArr[stackPtr--] : null;
    if (r != null) {
      r.onStack = false;
    }
    return r;
  }

  private Profiler.Record peek() {
    return stackPtr > -1 ? stackArr[stackPtr] : null;
  }

  private void addMeasured(Profiler.Record r) {
    if (measuredPtr == measuredSize) {
      compactMeasured();
    }
    measured[measuredPtr++] = r;
  }

  void reset() {
    try {
      while (!writerStatus.compareAndSet(0, 4)) {
        LockSupport.parkNanos(this, 600);
      }
      drainDelayedRecords();
      resetState();
    } finally {
      writerStatus.compareAndSet(4, 0);
    }
  }

  interface TestHook {
    void beforeSnapshotAcquire();

    void afterSnapshotAcquire();
  }

  void setTestHook(TestHook testHook) {
    this.testHook = testHook;
  }

  int delayedRecordCountForTest() {
    return delayedRecords.size();
  }

  /**
   * Discards accumulated measurements while keeping the currently-executing frames intact so their
   * pending exits are still handled. Must be called while holding an exclusive writer state (2 or
   * 4).
   *
   * <p>The still-live frames (indices {@code 0..stackPtr} of {@link #stackArr}) are carried into
   * the fresh {@code measured} buffer as live entries. Crucially the stack itself is NOT cleared:
   * the same {@link Profiler.Record} instances remain referenced by {@code stackArr} so that a
   * later {@code processExit} pops a real record instead of a null slot (which previously caused
   * NPEs and permanently corrupted the recorder).
   */
  private void resetState() {
    Profiler.Record[] newMeasured = new Profiler.Record[defaultBufferSize + stackPtr + 1];
    if (stackPtr > -1) {
      System.arraycopy(stackArr, 0, newMeasured, 0, stackPtr + 1);
    }
    indexMap.clear();
    measuredPtr = stackPtr + 1;
    measured = newMeasured;
    measuredSize = measured.length;
    lastIndex = measuredPtr;
    carryOver = 0L;
  }

  @SuppressWarnings("ManualMinMaxCalculation")
  private void compactMeasured() {
    int lastMeasurePtr = lastIndex;
    if (lastIndex >= measuredPtr) {
      return;
    }

    for (int i = lastIndex; i < measuredPtr; i++) {
      Profiler.Record m = measured[i];
      if (!m.onStack) {
        Integer newIndex = indexMap.get(m.blockName);
        if (newIndex == null) {
          newIndex = lastMeasurePtr++;
          indexMap.put(m.blockName, newIndex);
          measured[newIndex] = m;
        } else {
          Profiler.Record mr = measured[newIndex];
          mr.selfTime += m.selfTime;
          mr.wallTime += m.wallTime;
          mr.invocations++;
          mr.selfTimeMax = Math.max(m.selfTime, mr.selfTimeMax);
          mr.selfTimeMin = Math.min(m.selfTime, mr.selfTimeMin);
          mr.wallTimeMax = Math.max(m.wallTime, mr.wallTimeMax);
          mr.wallTimeMin = Math.min(m.wallTime, mr.wallTimeMin);
          m.referring = mr;
        }
      }
    }
    for (int j = 0; j < stackPtr; j++) {
      // if the old ref is kept on stack replace it with the compacted ref
      Profiler.Record mr = stackArr[j].referring;
      if (mr != null) {
        stackArr[j] = mr;
      }
    }
    if ((lastMeasurePtr + stackPtr + 1) == measuredSize) {
      int newMeasuredSize =
          ((measuredSize * 5) >> 2) + (stackPtr + 1); // make room for the methods on the stack
      if (newMeasuredSize == measuredSize) {
        newMeasuredSize =
            (measuredSize << 2) + (stackPtr + 1); // make room for the methods on the stack
      }
      Profiler.Record[] newMeasured = new Profiler.Record[newMeasuredSize];
      System.arraycopy(measured, 0, newMeasured, 0, lastMeasurePtr); // copy the compacted values
      measured = newMeasured;
      measuredSize = newMeasuredSize;
    }
    System.arraycopy(
        stackArr,
        0,
        measured,
        lastMeasurePtr,
        stackPtr + 1); // add the not processed methods on the stack
    measuredPtr = lastMeasurePtr + stackPtr + 1; // move the pointer behind the methods on the stack
    lastIndex = lastMeasurePtr;
  }

  private static class DelayedRecord {

    private final String blockName;
    private final long duration;

    public DelayedRecord(String blockName, long duration) {
      this.blockName = blockName;
      this.duration = duration;
    }
  }
}
