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

import com.sun.btrace.annotations.Property;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Profiler is a highly specialized aggregation-like data collector optimized
 * for high-speed collection of the application execution call tree data.
 * <br/><br/>
 * <note>It is exposable as MBean via {@linkplain Property} annotation</note>
 *
 * @since 1.2
 *
 * @author Jaroslav Bachorik
 */
public abstract class Profiler {
    /**
     * Record represents an atomic unit in the application execution call tree
     *
     * @since 1.2
     */
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
        public long wallTime = 0, wallTimeMax = 0, wallTimeMin = Long.MAX_VALUE;
        public long selfTime = 0, selfTimeMax = 0, selfTimeMin = Long.MAX_VALUE;
        public long invocations = 1;
        public boolean onStack = false;
        public Record referring = null;

        public Record(String blockName) {
            this.blockName = blockName;
        }

        public Record duplicate() {
            Record r = new Record(blockName);
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

    /**
     * Snapshot is an immutable image of the current profiling data collected
     * by the {@linkplain Profiler}
     * <br/><br/>
     * It is created by calling {@linkplain Profiler#snapshot()} method
     *
     * @since 1.2
     */
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

    /**
     * Helper interface to make accessing a {@linkplain Profiler} as an MBean
     * type safe.
     */
    public static interface MBeanValueProvider {
        Snapshot getMBeanValue();
    }

    /**
     * This property exposes the time of creating this particular {@linkplain Profiler} instance.
     * <br/>
     * The unit is milliseconds.
     */
    final public long START_TIME;

    /**
     * Creates a new {@linkplain Profiler} instance
     */
    public Profiler() {
        this.START_TIME = System.currentTimeMillis();
    }

    /**
     * Records the event of entering an execution unit (eg. method)<br/>
     * Must be paired with a call to {@linkplain Profiler#recordExit(java.lang.String, long) }
     * with the same blockName, eventually
     * @param blockName The execution unit identifier (eg. method FQN)
     */
    public abstract void recordEntry(String blockName);
    /**
     * Records the event of exiting an execution unit (eg. method)<br/>
     * Must be preceded by a call to {@linkplain Profiler#recordEntry(java.lang.String) }
     * with the same blockName
     * @param blockName The execution unit identifier (eg. method FQN)
     * @param duration Invocation duration in nanoseconds
     */
    public abstract void recordExit(String blockName, long duration);

    /**
     * Creates an immutable snapshot of the collected profiling data
     * @return Returns the immutable {@linkplain Snapshot} instance
     */
    final public Snapshot snapshot() {
        return snapshot(false);
    }
    /**
     * Creates an immutable snapshot of the collected profiling data.<br/>
     * Makes it possible to reset the profiler after creating the snapshot, eventually
     * @param reset Signals the profiler to perform reset right after getting the snapshot (in an atomic transaction)
     * @return Returns the immutable {@linkplain Snapshot} instance
     */
    public abstract Snapshot snapshot(boolean reset);

    /**
     * Resets all the collected data
     */
    public abstract void reset();
}
