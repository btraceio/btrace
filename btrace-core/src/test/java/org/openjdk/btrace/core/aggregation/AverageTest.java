/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.core.aggregation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AverageTest {

  private Average average;

  @BeforeEach
  void setUp() {
    average = new Average();
  }

  @Test
  void testEmptyAverage() {
    assertEquals(0L, average.getValue(), "Average of empty set should be 0");
  }

  @Test
  void testSimpleAverage() {
    average.add(10);
    average.add(20);
    average.add(30);
    assertEquals(20L, average.getValue(), "Average of 10, 20, 30 should be 20");
  }

  @Test
  void testLargeValues() {
    // Test values that would overflow if cast to int
    long largeValue = 3_000_000_000L; // > Integer.MAX_VALUE
    average.add(largeValue);
    average.add(largeValue);
    average.add(largeValue);

    assertEquals(largeValue, average.getValue(),
        "Average should preserve full 64-bit precision");
  }

  @Test
  void testLargeSum() {
    // Test where sum exceeds int range but average doesn't
    average.add(Integer.MAX_VALUE);
    average.add(Integer.MAX_VALUE);

    long expected = Integer.MAX_VALUE;
    assertEquals(expected, average.getValue(),
        "Average of large values should not truncate");
  }

  @Test
  void testNegativeValues() {
    average.add(-100);
    average.add(-200);
    average.add(-300);
    assertEquals(-200L, average.getValue(), "Average should handle negative values");
  }

  @Test
  void testMixedValues() {
    average.add(Long.MAX_VALUE / 2);
    average.add(Long.MAX_VALUE / 2);

    long expected = Long.MAX_VALUE / 2;
    assertEquals(expected, average.getValue(),
        "Average should not lose precision with very large sums");
  }

  @Test
  void testClear() {
    average.add(100);
    average.add(200);
    assertEquals(150L, average.getValue());

    average.clear();
    assertEquals(0L, average.getValue(), "Average after clear should be 0");

    average.add(50);
    assertEquals(50L, average.getValue(), "Average should work correctly after clear");
  }

  @Test
  void testGetData() {
    average.add(100);
    average.add(200);

    Object data = average.getData();
    assertInstanceOf(Long.class, data, "getData should return Long");
    assertEquals(150L, data, "getData should return same as getValue");
  }
}
