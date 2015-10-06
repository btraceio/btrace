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
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Jaroslav Bachorik
 */
public class IntervalTest {
    /**
     * Test of union method, of class Interval.
     * Checking intersecting intervals
     */
    @Test
    public void testUnionIntersectingIntervals() {
        System.out.println("Intersecting Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 6),
            new Interval(6, 10),
            new Interval(8, 12)
        );
        List<Interval> expResult = Arrays.asList(
            new Interval(1, 12)
        );
        List<Interval> result = Interval.union(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of invert method, of class Interval.
     * Checking intersecting intervals negation
     */
    @Test
    public void testInvertIntersectingIntervals() {
        System.out.println("Negate Intersecting Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 6),
            new Interval(6, 10),
            new Interval(8, 12)
        );
        List<Interval> expResult = Arrays.asList(
            Interval.lt(1),
            Interval.gt(12)
        );
        List<Interval> result = Interval.invert(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of invert method, of class Interval.
     * Checking intersecting intervals negation
     */
    @Test
    public void testInvertIntersectingIntervalsAll() {
        System.out.println("Negate Intersecting Intervals All");
        Collection<Interval> intervals = Arrays.asList(
            Interval.ge(1),
            Interval.lt(5)
        );
        List<Interval> expResult = Arrays.asList(
            Interval.none()
        );
        List<Interval> result = Interval.invert(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of union method, of class Interval.
     * Checking following intervals
     */
    @Test
    public void testUnionFollowingIntervals() {
        System.out.println("Following Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 6),
            new Interval(7, 10),
            new Interval(11, 12)
        );
        List<Interval> expResult = Arrays.asList(
            new Interval(1, 12)
        );
        List<Interval> result = Interval.union(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of invert method, of class Interval.
     * Checking following intervals negation
     */
    @Test
    public void testInvertFollowingIntervals() {
        System.out.println("Following Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 6),
            new Interval(7, 10),
            new Interval(11, 12)
        );
        List<Interval> expResult = Arrays.asList(
            Interval.lt(1),
            Interval.gt(12)
        );
        List<Interval> result = Interval.invert(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of union method, of class Interval.
     * Checking disparate intervals
     */
    @Test
    public void testUnionDisparateIntervals() {
        System.out.println("Disparate Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 3),
            new Interval(5, 8),
            new Interval(10, 12)
        );
        List<Interval> expResult = new ArrayList<>(intervals);
        List<Interval> result = Interval.union(intervals);
        assertEquals(expResult, result);
    }

    /**
     * Test of invert method, of class Interval.
     * Checking disparate intervals negation
     */
    @Test
    public void testInvertDisparateIntervals() {
        System.out.println("Disparate Intervals");
        Collection<Interval> intervals = Arrays.asList(
            new Interval(1, 3),
            new Interval(5, 8),
            new Interval(11, 12)
        );
        List<Interval> expResult = Arrays.asList(
            Interval.lt(1),
            Interval.eq(4),
            new Interval(9, 10),
            Interval.gt(12)
        );
        List<Interval> result = Interval.invert(intervals);
        assertEquals(expResult, result);
    }
}
