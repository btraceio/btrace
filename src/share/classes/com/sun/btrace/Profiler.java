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

package com.sun.btrace;

import java.util.Comparator;

/**
 *
 * @author Jaroslav Bachorik
 */
public abstract class Profiler {
    final public static class Record {
        final public static Comparator<Record> COMPARATOR = new Comparator<Record>() {
            public int compare(Record o1, Record o2) {
                if (o1 == null && o2 != null) return 1;
                if (o1 != null && o2 == null) return -1;
                if (o1 == null && o2 == null) return 0;
                return o1.blockName.compareTo(o2.blockName);
            }
        };

        final public String blockName;
        public long wallTime = 0;
        public long selfTime = 0;
        public long invocations = 1;
        public boolean onStack = false;

        public Record(String blockName) {
            this.blockName = blockName;
        }

        public Record duplicate() {
            Record r = new Record(blockName);
            r.invocations = invocations;
            r.selfTime = selfTime;
            r.wallTime = wallTime;
            return r;
        }
    }

    final public static class Snapshot {
        final public long timeStamp;
        final public Record[] total;
//        final public Record[] diff;

        public Snapshot(Record[] data, long timeStamp) {
            this.timeStamp = timeStamp;
            this.total = data;
//            this.diff = getDiff(data, baseData);
        }

//        /**
//         * Will calculate the diff data between the total and the baseData
//         * @param total The current total profiling data
//         * @param baseData A base profiling data
//         * @return Returns an array of {@linkplain Record} instances representing the diff between the base and the current state
//         */
//        private Record[] getDiff(Record[] total, Record[] baseData) {
//            int diffSize = baseData == null ? total.length : (total.length > baseData.length ? total.length : baseData.length);
//            Record[] rDiff = new Record[diffSize];
//            boolean[] flags = new boolean[baseData != null ? baseData.length : 0];
//            int i = 0;
//            for(i=0;i<total.length;i++) {
//                Record r = total[i];
//                rDiff[i] = new Record(r.blockName);
//                int baseIndex = baseData != null ? Arrays.binarySearch(baseData, r, Record.COMPARATOR) : -1;
//                if (baseIndex > -1) {
//                    Record r1 = baseData[baseIndex];
//                    rDiff[i].invocations = r.invocations - r1.invocations;
//                    rDiff[i].selfTime = r.selfTime - r1.selfTime;
//                    rDiff[i].wallTime = r.wallTime - r1.wallTime;
//                    flags[baseIndex] = true;
//                } else {
//                    rDiff[i].invocations = r.invocations;
//                    rDiff[i].selfTime = r.selfTime;
//                    rDiff[i].wallTime = r.wallTime;
//                }
//            }
//            if (baseData != null) {
//                for(int j=0;j<baseData.length;j++) {
//                    if (!flags[j]) {
//                        Record r = baseData[j];
//                        Record r1 = new Record(r.blockName);
//                        rDiff[i++] = r1;
//                        r1.invocations = -r.invocations;
//                        r1.selfTime = -r.selfTime;
//                        r1.wallTime = -r.wallTime;
//                    }
//                }
//            }
//            return rDiff;
//        }
    }

    public static interface MBeanValueProvider {
        Snapshot getMBeanValue();
    }

    final public long START_TIME;

    public Profiler() {
        this.START_TIME = System.currentTimeMillis();
    }

    public abstract void recordEntry(String methodName);
    public abstract void recordExit(String methodName, long duration);
    final public Snapshot snapshot() {
        return snapshot(false);
    }
    public abstract Snapshot snapshot(boolean reset);
    public abstract void reset();
}
