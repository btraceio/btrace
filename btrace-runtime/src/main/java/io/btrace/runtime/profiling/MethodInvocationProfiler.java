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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import io.btrace.core.Profiler;

/**
 * Implementation of {@linkplain Profiler}
 *
 * @author Jaroslav Bachorik
 */
public class MethodInvocationProfiler extends Profiler implements Profiler.MBeanValueProvider {

  private final Collection<WeakReference<MethodInvocationRecorder>> recorders =
      new ConcurrentLinkedDeque<>();
  private final int expectedBlockCnt;
  private final ThreadLocal<MethodInvocationRecorder> recorder =
      new ThreadLocal<MethodInvocationRecorder>() {
        @Override
        protected MethodInvocationRecorder initialValue() {
          MethodInvocationRecorder mir = new MethodInvocationRecorder(expectedBlockCnt);
          recorders.add(new WeakReference<>(mir));
          return mir;
        }
      };
  private volatile Snapshot lastValidSnapshot = null;
  private long lastTs = START_TIME;

  public MethodInvocationProfiler(int expectedMethodCnt) {
    expectedBlockCnt = expectedMethodCnt;
  }

  @Override
  public void recordEntry(String blockName) {
    recorder.get().recordEntry(blockName);
  }

  @Override
  public void recordExit(String blockName, long duration) {
    recorder.get().recordExit(blockName, duration);
  }

  @Override
  public void reset() {
    for (WeakReference<MethodInvocationRecorder> mirRef : recorders) {
      MethodInvocationRecorder mir = mirRef.get();
      if (mir != null) {
        mir.reset();
      }
    }
  }

  @Override
  public Snapshot snapshot(boolean reset) {
    Map<String, Integer> idMap = new HashMap<>();

    Record[] mergedRecords = null;
    int mergedEntries = 0, mergedCapacity = 0;
    for (WeakReference<MethodInvocationRecorder> mirRef : recorders) {
      MethodInvocationRecorder mir = mirRef.get();
      if (mir == null) continue;

      Record[] records = mir.getRecords(reset);
      if (records == null || records.length == 0) continue; // just skip the empty data

      if (mergedRecords == null) {
        mergedRecords = records;
        mergedCapacity = mergedRecords.length;
        for (int i = 0; i < records.length; i++) {
          if (records[i] != null) {
            mergedEntries = i + 1;
            idMap.put(records[i].blockName, i);
          }
        }
        continue;
      }

      for (Record r : records) {
        Integer id = idMap.get(r.blockName);
        if (id == null) {
          id = mergedEntries++;
          if (mergedEntries > mergedCapacity) {
            mergedCapacity = (((mergedEntries + 1) * 5) >> 2);
            Record[] newRecs = new Record[mergedCapacity];
            System.arraycopy(mergedRecords, 0, newRecs, 0, mergedEntries - 1);
            mergedRecords = newRecs;
          }
          idMap.put(r.blockName, id);
          mergedRecords[id] = r;
        } else {
          Record merged = mergedRecords[id];
          merged.invocations += r.invocations;
          merged.selfTime += r.selfTime;
          merged.wallTime += r.wallTime;
        }
      }
    }
    Record[] rslt = new Record[mergedEntries];
    if (mergedRecords != null) {
      System.arraycopy(mergedRecords, 0, rslt, 0, mergedEntries);
    }

    long curTs = System.currentTimeMillis();
    Snapshot snp = new Snapshot(rslt, lastTs, curTs);
    lastTs = curTs;
    lastValidSnapshot = snp;
    return snp;
  }

  @Override
  public Snapshot getMBeanValue() {
    return lastValidSnapshot;
  }
}
