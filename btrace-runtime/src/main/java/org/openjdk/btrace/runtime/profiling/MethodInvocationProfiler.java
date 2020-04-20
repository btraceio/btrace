/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.runtime.profiling;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.openjdk.btrace.core.Profiler;

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
