/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.profiling;

import com.sun.btrace.Profiler;
import java.util.HashMap;
import java.util.Map;

/**
 *
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
        private int compactedPtr = 0;
        private Record[] measured = new Record[0];

        private long carryOver = 0L;

        public MethodInvocationRecorder(int expectedBlockCnt) {
            measuredSize = expectedBlockCnt * 10;
            measured = new Record[measuredSize];
        }


        private synchronized void recordEntry(String blockName) {
            Record r = new Record(blockName);
            addMeasured(r);
            push(r);
            carryOver = 0L; // clear the carryOver; not 2 subsequent calls to recordExit
        }

        private synchronized void recordExit(String blockName, long duration) {
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

            Record parent = peek();
            if (parent != null) {
                parent.selfTime -= duration;
            } else {
                carryOver = duration;
            }
        }

        private Record[] getRecords(boolean reset) {
            Record[] recs = null;
            synchronized(this) {
                // compact the collected data
                int stopPtr = compactMeasured();
                recs = new Record[stopPtr];
                // copy the record array
                System.arraycopy(measured, 0, recs, 0, stopPtr);
                // call reset if requested
                if (reset) reset();

                // detach the real records so they don't change as the measuring goes on
                for(int i=0;i<recs.length;i++) {
                    recs[i] = recs[i].duplicate();
                }
                return recs;
            }
        }

        private void push(Record r) {
            if (stackPtr > stackBndr) {
                stackSize *= 1.5;
                stackBndr *= 1.5;
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

        private synchronized void reset() {
            if (stackPtr > -1) {
                System.arraycopy(stackArr, 0, measured, 0, stackPtr + 1);
            }
            measuredPtr = stackPtr + 1;
        }

        private int compactMeasured() {
            Map<String, Integer> indexMap = new HashMap<String, Integer>();
            int lastIndex = 0;
            for(int i=0;i<measuredPtr;i++) {
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
                        if (compactedPtr < i) {
                            mr.selfTimeMax = m.selfTime > mr.selfTimeMax ? m.selfTime : mr.selfTimeMax;
                            mr.selfTimeMin = m.selfTime < mr.selfTimeMin ? m.selfTime : mr.selfTimeMin;
                            mr.wallTimeMax = m.wallTime > mr.wallTimeMax ? m.wallTime : mr.wallTimeMax;
                            mr.wallTimeMin = m.wallTime < mr.wallTimeMin ? m.wallTime : mr.wallTimeMin;
                        }
                        for(int j=0;j<stackPtr;j++) {
                            if (stackArr[j] == m) { // if the old ref is kept on stack
                                stackArr[j] = mr; // replace it with the compacted ref
                            }
                        }
                    }
                }
            }
            if ((lastIndex + stackPtr + 1) == measuredSize) {
                int newMeasuredSize = (int)(measuredSize * 1.25) + (stackPtr + 1); // make room for the methods on the stack
                if (newMeasuredSize == measuredSize) {
                    newMeasuredSize = measuredSize * 2 + (stackPtr + 1); // make room for the methods on the stack
                }
                Record[] newMeasured = new Record[newMeasuredSize];
                System.arraycopy(measured, 0, newMeasured, 0, lastIndex); // copy the compacted values
                measured = newMeasured;
            }
            System.arraycopy(stackArr, 0, measured, lastIndex, stackPtr + 1); // add the not processed methods on the stack
            measuredPtr = lastIndex + stackPtr + 1; // move the pointer behind the methods on the stack
            compactedPtr = measuredPtr;
            
            return lastIndex;
        }
    }

    final private Map<Thread, MethodInvocationRecorder> recorders = new HashMap<Thread, MethodInvocationRecorder>(128);


    volatile private Snapshot lastValidSnapshot = null;

    private int expectedBlockCnt;

    public MethodInvocationProfiler(int expectedMethodCnt) {
        this.expectedBlockCnt = expectedMethodCnt;
    }

    public void recordEntry(String blockName) {
        getThreadSampler().recordEntry(blockName);
    }

    public void recordExit(String blockName, long duration) {
        getThreadSampler().recordExit(blockName, duration);
    }

    public void reset() {
        synchronized(recorders) {
            for(MethodInvocationRecorder r : recorders.values()) {
                r.reset();
            }
        }
    }

    private long lastTs = START_TIME;

    public Snapshot snapshot(boolean reset) {
        synchronized(recorders) {
            Map<String, Integer> idMap = new HashMap<String, Integer>();

            Record[] mergedRecords = null;
            int mergedEntries = 0, mergedCapacity = 0;
            for(Map.Entry<Thread, MethodInvocationRecorder> sEntry : recorders.entrySet()) {
                final Record[] records = sEntry.getValue().getRecords(reset);
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

                for(int i=0;i<records.length;i++) {
                    Record r = records[i];
                    Integer id = idMap.get(r.blockName);
                    if (id == null) {
                        id = mergedEntries++;
                        if (mergedEntries > mergedCapacity) {
                            mergedCapacity = (int)((mergedEntries + 1) * 1.25);
                            Record[] newRecs = new Record[mergedCapacity];
                            System.arraycopy(mergedRecords, 0, newRecs, 0, mergedEntries - 1);
                            mergedRecords = newRecs;
                            idMap.put(r.blockName, id);
                            mergedRecords[id] = r;
                        }
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
    }

    public Snapshot getMBeanValue() {
        return lastValidSnapshot;
    }
    
    private MethodInvocationRecorder getThreadSampler() {
        Thread t = Thread.currentThread();
        MethodInvocationRecorder s = recorders.get(t);
        if (s == null) {
            synchronized(recorders) {
                if (!recorders.containsKey(t)) {
                    s = new MethodInvocationRecorder(expectedBlockCnt);
                    recorders.put(t, s);
                }
            }
        }
        return s;
    }
}
