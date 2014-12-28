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

package com.sun.btrace.profiling;

import com.sun.btrace.Profiler;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementation of {@linkplain Profiler}
 * @author Jaroslav Bachorik
 */
public class MethodInvocationProfiler extends Profiler implements Profiler.MBeanValueProvider {
    private static class MethodInvocationRecorder {
        private int stackSize = 200;
        private int stackPtr = -1;
        private int stackBndr = 150;
        private Record[] stackArr = new Record[stackSize];

        private int measuredSize = 0;
        private int measuredPtr = 0;
        private Record[] measured = new Record[0];

        private long carryOver = 0L;
        private final int defaultBufferSize;

        private final Map<String, Integer> indexMap = new HashMap<String, Integer>();
        private int lastIndex = 0;

        public MethodInvocationRecorder(int expectedBlockCnt) {
            defaultBufferSize = expectedBlockCnt * 10;
            measuredSize = defaultBufferSize;
            measured = new Record[measuredSize];
        }

        private void recordEntry(String blockName) {
            Record r = new Record(blockName);
            addMeasured(r);
            push(r);
            carryOver = 0L; // clear the carryOver; not 2 subsequent calls to recordExit
        }

        private void recordExit(String blockName, long duration) {
            Record r = pop();
            if (r == null) {
                r = new Record(blockName);
                addMeasured(r);
            }
            r.wallTime = duration;
            r.selfTime += duration - carryOver;

            for(int i=0;i<stackPtr;i++) {
                if (stackArr[i].blockName.equals(blockName)) {
                    r.wallTime = 0;
                    break;
                }
            }
            r.selfTimeMin = r.selfTimeMax = r.selfTime;
            r.wallTimeMin = r.wallTimeMax = r.wallTime;

            Record parent = peek();
            if (parent != null) {
                parent.selfTime -= duration;
            } else {
                carryOver = duration;
            }
        }

        private Record[] getRecords(boolean reset) {
            Record[] recs = null;
            // compact the collected data
            int stopPtr = compactMeasured();
            recs = new Record[stopPtr];
            // copy and detach the record array
            for(int i=0;i<recs.length;i++) {
                recs[i] = measured[i].duplicate();
            }
            // call reset if requested
            if (reset) reset();

            return recs;
        }

        private void push(Record r) {
            if (stackPtr > stackBndr) {
                stackSize = (stackSize * 3) >> 1;
                stackBndr = (stackBndr * 3) >> 1;
                Record[] newStack = new Record[stackSize];
                System.arraycopy(stackArr, 0, newStack, 0, stackPtr + 1);
                stackArr = newStack;
            }
            stackArr[++stackPtr] = r;
            r.onStack = true;
        }

        private Record pop() {
            Record r = stackPtr > -1 ? stackArr[stackPtr--] : null;
            if (r != null) {
                r.onStack = false;
            }
            return r;
        }

        private Record peek() {
            return stackPtr > -1 ? stackArr[stackPtr] : null;
        }

        private void addMeasured(Record r) {
            if (measuredPtr == measuredSize) {
                compactMeasured();
            }
            measured[measuredPtr++] = r;
        }

        private void reset() {
            Record[] newMeasured = new Record[defaultBufferSize + stackPtr + 1];
            if (stackPtr > -1) {
                System.arraycopy(stackArr, 0, newMeasured, 0, stackPtr + 1);
            }
            measuredPtr = stackPtr + 1;
            measured = newMeasured;
            measuredSize = measured.length;
        }

        private int compactMeasured() {
            for(int i=lastIndex;i<measuredPtr;i++) {
                Record m = measured[i];
                if (!m.onStack) {
                    Integer newIndex = indexMap.get(m.blockName);
                    if (newIndex == null) {
                        newIndex = lastIndex++;
                        indexMap.put(m.blockName, newIndex);
                        measured[newIndex] = m;
                    } else {
                        Record mr = measured[newIndex];
                        mr.selfTime += m.selfTime;
                        mr.wallTime += m.wallTime;
                        mr.invocations++;
                        mr.selfTimeMax = m.selfTime > mr.selfTimeMax ? m.selfTime : mr.selfTimeMax;
                        mr.selfTimeMin = m.selfTime < mr.selfTimeMin ? m.selfTime : mr.selfTimeMin;
                        mr.wallTimeMax = m.wallTime > mr.wallTimeMax ? m.wallTime : mr.wallTimeMax;
                        mr.wallTimeMin = m.wallTime < mr.wallTimeMin ? m.wallTime : mr.wallTimeMin;
                        m.referring = mr;
                    }
                }
            }
            for(int j=0;j<stackPtr;j++) {
                // if the old ref is kept on stack replace it with the compacted ref
                Record mr = stackArr[j].referring;
                if (mr != null) {
                    stackArr[j] = mr;
                }
            }
            if ((lastIndex + stackPtr + 1) == measuredSize) {
                int newMeasuredSize = ((int)(measuredSize * 5) >> 2) + (stackPtr + 1); // make room for the methods on the stack
                if (newMeasuredSize == measuredSize) {
                    newMeasuredSize = (measuredSize << 2) + (stackPtr + 1); // make room for the methods on the stack
                }
                Record[] newMeasured = new Record[newMeasuredSize];
                System.arraycopy(measured, 0, newMeasured, 0, lastIndex); // copy the compacted values
                measured = newMeasured;
                measuredSize = newMeasuredSize;
            }
            System.arraycopy(stackArr, 0, measured, lastIndex, stackPtr + 1); // add the not processed methods on the stack
            measuredPtr = lastIndex + stackPtr + 1; // move the pointer behind the methods on the stack

            return lastIndex;
        }
    }

    final private Collection<WeakReference<MethodInvocationRecorder>> recorders = new ConcurrentLinkedQueue<WeakReference<MethodInvocationRecorder>>();

    final private ThreadLocal<MethodInvocationRecorder> recorder = new ThreadLocal<MethodInvocationRecorder>(){
        @Override
        protected MethodInvocationRecorder initialValue() {
            MethodInvocationRecorder mir = new MethodInvocationRecorder(expectedBlockCnt);
            recorders.add(new WeakReference<MethodInvocationRecorder>(mir));
            return mir;
        }
    };

    volatile private Snapshot lastValidSnapshot = null;

    private final int expectedBlockCnt;

    public MethodInvocationProfiler(int expectedMethodCnt) {
        this.expectedBlockCnt = expectedMethodCnt;
    }

    public void recordEntry(String blockName) {
        recorder.get().recordEntry(blockName);
    }

    public void recordExit(String blockName, long duration) {
        recorder.get().recordExit(blockName, duration);
    }

    public void reset() {
        for(WeakReference<MethodInvocationRecorder> mirRef : recorders) {
            MethodInvocationRecorder mir = mirRef.get();
            if (mir != null) {
                mir.reset();
            }
        }
    }

    private long lastTs = START_TIME;

    public Snapshot snapshot(boolean reset) {
        Map<String, Integer> idMap = new HashMap<String, Integer>();

        Record[] mergedRecords = null;
        int mergedEntries = 0, mergedCapacity = 0;
        for(WeakReference<MethodInvocationRecorder> mirRef : recorders) {
            MethodInvocationRecorder mir = mirRef.get();
            if (mir == null) continue;

            final Record[] records = mir.getRecords(reset);
            if (records == null || records.length == 0) continue; // just skip the empty data

            if (mergedRecords == null) {
                mergedRecords = records;
                mergedCapacity = mergedRecords.length;
                for(int i=0;i<records.length;i++) {
                    if (records[i] != null) {
                        mergedEntries = i + 1;
                    }
                    idMap.put(records[i].blockName, i);
                }
                continue;
            }

            for (Record r : records) {
                Integer id = idMap.get(r.blockName);
                if (id == null) {
                    id = mergedEntries++;
                    if (mergedEntries > mergedCapacity) {
                        mergedCapacity = ((int)((mergedEntries + 1) * 5) >> 2);
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

    public Snapshot getMBeanValue() {
        return lastValidSnapshot;
    }
}
