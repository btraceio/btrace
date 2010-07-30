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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        public long threadId = Thread.currentThread().getId();
        public long wallTime = 0, wallTimeMax = 0, wallTimeMin = Long.MAX_VALUE;
        public long selfTime = 0, selfTimeMax = 0, selfTimeMin = Long.MAX_VALUE;
        public long invocations = 1;
        public boolean onStack = false;

        public Record(String blockName) {
            this.blockName = blockName;
        }

        public Record duplicate() {
            Record r = new Record(blockName);
            r.threadId = threadId;
            r.invocations = invocations;
            r.selfTime = selfTime;
            r.selfTimeMax = selfTimeMax;
            r.selfTimeMin = selfTimeMin;
            r.wallTime = wallTime;
            r.wallTimeMax = wallTimeMax;
            r.wallTimeMin = wallTimeMin;
            return r;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Record other = (Record) obj;
            if ((this.blockName == null) ? (other.blockName != null) : !this.blockName.equals(other.blockName)) {
                return false;
            }
            if (this.wallTime != other.wallTime) {
                return false;
            }
            if (this.selfTime != other.selfTime) {
                return false;
            }
            if (this.invocations != other.invocations) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + (this.blockName != null ? this.blockName.hashCode() : 0);
            hash = 17 * hash + (int) (this.wallTime ^ (this.wallTime >>> 32));
            hash = 17 * hash + (int) (this.selfTime ^ (this.selfTime >>> 32));
            hash = 17 * hash + (int) (this.invocations ^ (this.invocations >>> 32));
            return hash;
        }

        @Override
        public String toString() {
            return "Record{\n" + "\tblockName=" + blockName + "\n," +
                   "\twallTime=" + wallTime + ",\n" +
                   "\twallTime.min=" + wallTimeMin + ",\n" +
                   "\twallTime.max=" + wallTimeMax + ",\n" +
                   "\tselfTime=" + selfTime + ",\n" +
                   "\tselfTime.min=" + selfTimeMin + ",\n" +
                   "\tselfTime.max=" + selfTimeMax + ",\n" +
                   "\tinvocations=" + invocations + ",\nonStack=" + onStack + '}';
        }
    }

    final public static class Snapshot {
        final public long timeStamp;
        final public long timeInterval;
        final public Record[] total;

        public Snapshot(Record[] data, long startTs, long stopTs) {
            this.timeStamp = stopTs;
            this.timeInterval = stopTs - startTs;
            this.total = data;
        }

        List<Object[]> getGridData() {
            List<Object[]>  rslt = new ArrayList<Object[]>();

            Object[] titleRow = new Object[]{"Block", "Invocations", "SelfTime.Total", "SelfTime.Avg", "SelfTime.Min",
                                             "SelfTime.Max", "WallTime.Total", "WallTime.Avg", "WallTime.Min", "WallTime.Max"};

            rslt.add(titleRow);

            for(Record r : total) {
                if (r != null) {
                    Object[] row = new Object[]{r.blockName, r.invocations, r.selfTime, r.selfTime / r.invocations,
                                                r.selfTimeMin < Long.MAX_VALUE ? r.selfTimeMin : "N/A",
                                                r.selfTimeMax > 0 ? r.selfTimeMax : "N/A",
                                                r.wallTime, r.wallTime / r.invocations,
                                                r.wallTimeMin < Long.MAX_VALUE ? r.wallTimeMin : "N/A",
                                                r.wallTimeMax > 0 ? r.wallTimeMax : "N/A"
                    };
                    rslt.add(row);
                }
            }
            return rslt;
        }
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
