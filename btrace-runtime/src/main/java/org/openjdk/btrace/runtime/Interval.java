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
package org.openjdk.btrace.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jaroslav Bachorik
 */
public class Interval implements Comparable<Interval> {
  private static final Pattern INTERVAL_PATTERN = Pattern.compile("\\((\\d+)[,;]\\s*(\\d+)\\)");
  private static final Pattern COMP_PATTERN =
      Pattern.compile("(<|>|=|<=|>=|GT|LT|GE|LE|EQ)?(\\d+)");

  private int a, b;

  public Interval(int a, int b) {
    this.a = a;
    this.b = b;
  }

  public static Interval eq(int value) {
    return new Interval(value, value);
  }

  public static Interval ge(int value) {
    return new Interval(value, Integer.MAX_VALUE);
  }

  public static Interval gt(int value) {
    return new Interval(
        value != Integer.MAX_VALUE ? value + 1 : Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public static Interval le(int value) {
    return new Interval(Integer.MIN_VALUE, value);
  }

  public static Interval lt(int value) {
    return new Interval(
        Integer.MIN_VALUE, value != Integer.MIN_VALUE ? value - 1 : Integer.MIN_VALUE);
  }

  public static Interval all() {
    return new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  public static Interval none() {
    return new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
  }

  public static List<Interval> union(Collection<Interval> intervals) {
    Set<Interval> itvSet = new TreeSet<>(intervals);

    Iterator<Interval> iter = itvSet.iterator();
    Interval previous = null;
    while (iter.hasNext()) {
      if (previous == null) {
        previous = iter.next();
        continue;
      }
      Interval current = iter.next();
      if (current.a <= (previous.b != Integer.MAX_VALUE ? previous.b + 1 : Integer.MAX_VALUE)) {
        previous.b = current.b;
        iter.remove();
      } else {
        previous = current;
      }
    }
    return new ArrayList<>(itvSet);
  }

  public static List<Interval> invert(Collection<Interval> intervals) {
    Interval remainder = new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
    Set<Interval> sorted = new TreeSet<>(union(intervals));
    List<Interval> result = new ArrayList<>();
    for (Interval i : sorted) {
      if (i.isAll()) {
        return Collections.singletonList(none());
      }
      if (i.a <= remainder.a) {
        if (i.b > remainder.a) remainder.a = i.b != Integer.MAX_VALUE ? i.b + 1 : i.b;
      } else {
        result.add(new Interval(remainder.a, i.a - 1));
        if (i.b < remainder.b) {
          remainder.a = i.b + 1;
        } else {
          remainder = null;
          break;
        }
      }
    }
    if (remainder != null) {
      result.add(remainder);
    }
    return result;
  }

  public static Interval fromString(String s) {
    Matcher m = INTERVAL_PATTERN.matcher(s);
    if (m.matches()) {
      int a = Integer.parseInt(m.group(1));
      int b = Integer.parseInt(m.group(2));
      return new Interval(a, b);
    } else {
      m = COMP_PATTERN.matcher(s);
      if (m.matches()) {
        String operator = m.group(1) != null ? m.group(1) : "";
        String val = m.group(2);
        int level = Integer.parseInt(val);
        switch (operator) {
          case "EQ":
          case "=":
            {
              return eq(level);
            }
          case "LT":
          case "<":
            {
              return lt(level);
            }
          case "GT":
          case ">":
            {
              return gt(level);
            }
          case "LE":
          case "<=":
            {
              return le(level);
            }
          case "GE":
          case ">=":
          case "":
            {
              return ge(level);
            }
          default:
            {
              throw new IllegalArgumentException("Unrecognized operator: " + operator);
            }
        }
      }
    }
    throw new IllegalArgumentException("Invalid level declaration: " + s);
  }

  public int getA() {
    return a;
  }

  public int getB() {
    return b;
  }

  public boolean isAll() {
    return a == Integer.MIN_VALUE && b == Integer.MAX_VALUE;
  }

  public boolean isNone() {
    return a == Integer.MAX_VALUE;
  }

  @Override
  public int compareTo(Interval o) {
    if (a < o.a) {
      return -1;
    } else if (a > o.a) {
      return 1;
    } else {
      return Integer.compare(b, o.b);
    }
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 23 * hash + a;
    hash = 23 * hash + b;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Interval other = (Interval) obj;
    if (a != other.a) {
      return false;
    }
    return b == other.b;
  }

  @Override
  public String toString() {
    if (a == Integer.MIN_VALUE) {
      if (b != Integer.MAX_VALUE) {
        return "LE" + b;
      }
    } else {
      if (b == Integer.MAX_VALUE) {
        return "GE" + a;
      }
    }
    return "(" + a + ";" + b + ")";
  }
}
