/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.util.templates.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Jaroslav Bachorik
 */
class Interval implements Comparable<Interval>{
    private int a, b;

    Interval(int a, int b) {
        this.a = a;
        this.b = b;
    }

    int getA() {
        return a;
    }

    int getB() {
        return b;
    }

    static Interval eq(int value) {
        return new Interval(value, value);
    }

    static Interval ge(int value) {
        return new Interval(value, Integer.MAX_VALUE);
    }

    static Interval gt(int value) {
        return new Interval(value != Integer.MAX_VALUE ? value + 1 : Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    static Interval le(int value) {
        return new Interval(Integer.MIN_VALUE, value);
    }

    static Interval lt(int value) {
        return new Interval(Integer.MIN_VALUE, value != Integer.MIN_VALUE ?value - 1 : Integer.MIN_VALUE);
    }

    static Interval all() {
        return new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    static Interval none() {
        return new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    public boolean isAll() {
        return a == Integer.MIN_VALUE && b == Integer.MAX_VALUE;
    }

    public boolean isNone() {
        return a == Integer.MAX_VALUE;
    }

    public static List<Interval> union(Collection<Interval> intervals) {
        Set<Interval> itvSet = new TreeSet<>();
        itvSet.addAll(intervals);

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
        return new LinkedList<>(itvSet);
    }

    public static List<Interval> invert(Collection<Interval> intervals) {
        Interval remainder = new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Set<Interval> sorted = new TreeSet(union(intervals));
        List<Interval> result = new LinkedList<>();
        for(Interval i : sorted) {
            if (i.isAll()) {
                return Collections.singletonList(Interval.none());
            }
            if (i.a <= remainder.a) {
                if (i.b > remainder.a)
                remainder.a = i.b != Integer.MAX_VALUE ? i.b + 1 : i.b;
            } else {
                result.add(new Interval(remainder.a, i.a - 1));
                if (i.b < remainder.b) {
                    remainder.a = i.b != Integer.MAX_VALUE ? i.b + 1 : i.b;
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

    @Override
    public int compareTo(Interval o) {
        if (a < o.a) {
            return -1;
        } else if (a > o.a) {
            return 1;
        } else {
            if (b < o.b) {
                return -1;
            } else if (b > o.b) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + this.a;
        hash = 23 * hash + this.b;
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
        final Interval other = (Interval) obj;
        if (this.a != other.a) {
            return false;
        }
        return this.b == other.b;
    }

    @Override
    public String toString() {
        return "Interval{" + "a=" + a + ", b=" + b + '}';
    }
}
