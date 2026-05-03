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
package io.btrace.core;

import io.btrace.core.annotations.Property;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Profiler is a highly specialized aggregation-like data collector optimized for high-speed
 * collection of the application execution call tree data. <br>
 * <br>
 * <cite>It is exposable as MBean via {@linkplain Property} annotation</cite>
 *
 * @author Jaroslav Bachorik
 * @since 1.2
 */
public abstract class Profiler {
  /**
   * This property exposes the time of creating this particular {@linkplain Profiler} instance. <br>
   * The unit is milliseconds.
   */
  public final long START_TIME;

  /** Creates a new {@linkplain Profiler} instance */
  public Profiler() {
    START_TIME = System.currentTimeMillis();
  }

  /**
   * Records the event of entering an execution unit (eg. method)<br>
   * Must be paired with a call to {@linkplain Profiler#recordExit(java.lang.String, long) } with
   * the same blockName, eventually
   *
   * @param blockName The execution unit identifier (eg. method FQN)
   */
  public abstract void recordEntry(String blockName);

  /**
   * Records the event of exiting an execution unit (eg. method)<br>
   * Must be preceded by a call to {@linkplain Profiler#recordEntry(java.lang.String) } with the
   * same blockName
   *
   * @param blockName The execution unit identifier (eg. method FQN)
   * @param duration Invocation duration in nanoseconds
   */
  public abstract void recordExit(String blockName, long duration);

  /**
   * Creates an immutable snapshot of the collected profiling data
   *
   * @return Returns the immutable {@linkplain Snapshot} instance
   */
  public final Snapshot snapshot() {
    return snapshot(false);
  }

  /**
   * Creates an immutable snapshot of the collected profiling data.<br>
   * Makes it possible to reset the profiler after creating the snapshot, eventually
   *
   * @param reset Signals the profiler to perform reset right after getting the snapshot (in an
   *     atomic transaction)
   * @return Returns the immutable {@linkplain Snapshot} instance
   */
  public abstract Snapshot snapshot(boolean reset);

  /** Resets all the collected data */
  public abstract void reset();

  /** Helper interface to make accessing a {@linkplain Profiler} as an MBean type safe. */
  public interface MBeanValueProvider {
    Snapshot getMBeanValue();
  }

  /**
   * Record represents an atomic unit in the application execution call tree
   *
   * @since 1.2
   */
  public static final class Record {
    public static final Comparator<Record> COMPARATOR =
        (o1, o2) -> {
          if (o1 == null && o2 != null) return 1;
          if (o1 != null && o2 == null) return -1;
          if (o1 == null && o2 == null) return 0;
          return o1.blockName.compareTo(o2.blockName);
        };

    public final String blockName;
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
      Record other = (Record) obj;
      if (!Objects.equals(blockName, other.blockName)) {
        return false;
      }
      if (wallTime != other.wallTime) {
        return false;
      }
      if (selfTime != other.selfTime) {
        return false;
      }
      return invocations == other.invocations;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 17 * hash + (blockName != null ? blockName.hashCode() : 0);
      hash = 17 * hash + (int) (wallTime ^ (wallTime >>> 32));
      hash = 17 * hash + (int) (selfTime ^ (selfTime >>> 32));
      hash = 17 * hash + (int) (invocations ^ (invocations >>> 32));
      return hash;
    }

    @Override
    public String toString() {
      return "Record{\n"
          + "\tblockName="
          + blockName
          + "\n,"
          + "\twallTime="
          + wallTime
          + ",\n"
          + "\twallTime.min="
          + wallTimeMin
          + ",\n"
          + "\twallTime.max="
          + wallTimeMax
          + ",\n"
          + "\tselfTime="
          + selfTime
          + ",\n"
          + "\tselfTime.min="
          + selfTimeMin
          + ",\n"
          + "\tselfTime.max="
          + selfTimeMax
          + ",\n"
          + "\tinvocations="
          + invocations
          + ",\nonStack="
          + onStack
          + '}';
    }
  }

  /**
   * Snapshot is an immutable image of the current profiling data collected by the {@linkplain
   * Profiler} <br>
   * <br>
   * It is created by calling {@linkplain Profiler#snapshot()} method
   *
   * @since 1.2
   */
  public static final class Snapshot {
    public final long timeStamp;
    public final long timeInterval;
    public final Record[] total;

    public Snapshot(Record[] data, long startTs, long stopTs) {
      timeStamp = stopTs;
      timeInterval = stopTs - startTs;
      total = data;
    }

    public List<Object[]> getGridData() {
      List<Object[]> rslt = new ArrayList<>();

      Object[] titleRow = {
        "Block",
        "Invocations",
        "SelfTime.Total",
        "SelfTime.Avg",
        "SelfTime.Min",
        "SelfTime.Max",
        "WallTime.Total",
        "WallTime.Avg",
        "WallTime.Min",
        "WallTime.Max"
      };

      rslt.add(titleRow);

      for (Record r : total) {
        if (r != null) {
          Object[] row = {
            r.blockName,
            r.invocations,
            r.selfTime,
            r.selfTime / r.invocations,
            r.selfTimeMin < Long.MAX_VALUE ? r.selfTimeMin : "N/A",
            r.selfTimeMax > 0 ? r.selfTimeMax : "N/A",
            r.wallTime,
            r.wallTime / r.invocations,
            r.wallTimeMin < Long.MAX_VALUE ? r.wallTimeMin : "N/A",
            r.wallTimeMax > 0 ? r.wallTimeMax : "N/A"
          };
          rslt.add(row);
        }
      }
      return rslt;
    }
  }
}
