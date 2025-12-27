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

class MinimumTest {

  private Minimum minimum;

  @BeforeEach
  void setUp() {
    minimum = new Minimum();
  }

  @Test
  void testInitialValue() {
    assertEquals(Long.MAX_VALUE, minimum.getValue(),
        "Initial minimum should be Long.MAX_VALUE");
  }

  @Test
  void testSimpleMinimum() {
    minimum.add(30);
    minimum.add(10);
    minimum.add(20);
    assertEquals(10L, minimum.getValue(), "Minimum of 30, 10, 20 should be 10");
  }

  @Test
  void testNegativeValues() {
    minimum.add(-10);
    minimum.add(-50);
    minimum.add(-5);
    assertEquals(-50L, minimum.getValue(), "Minimum should handle negative values");
  }

  @Test
  void testMixedValues() {
    minimum.add(100);
    minimum.add(-50);
    minimum.add(0);
    assertEquals(-50L, minimum.getValue(), "Minimum of mixed values should be -50");
  }

  @Test
  void testLargeValues() {
    long largeValue1 = Long.MAX_VALUE - 100;
    long largeValue2 = Long.MAX_VALUE - 50;
    minimum.add(largeValue2);
    minimum.add(largeValue1);

    assertEquals(largeValue1, minimum.getValue(),
        "Minimum should handle large values correctly");
  }

  @Test
  void testValuesExceedingIntegerRange() {
    // Test values > Integer.MAX_VALUE
    long value1 = 3_000_000_000L;  // > Integer.MAX_VALUE
    long value2 = 4_000_000_000L;

    minimum.add(value2);
    minimum.add(value1);

    assertEquals(value1, minimum.getValue(),
        "Minimum should work correctly with values > Integer.MAX_VALUE");
  }

  @Test
  void testClearResetsToLongMaxValue() {
    minimum.add(10);
    minimum.add(5);
    assertEquals(5L, minimum.getValue());

    minimum.clear();
    assertEquals(Long.MAX_VALUE, minimum.getValue(),
        "After clear(), minimum should reset to Long.MAX_VALUE");
  }

  @Test
  void testClearThenAddLargeValue() {
    minimum.add(100);
    minimum.clear();

    // Add a value > Integer.MAX_VALUE
    long largeValue = 3_000_000_000L;
    minimum.add(largeValue);

    assertEquals(largeValue, minimum.getValue(),
        "After clear(), should correctly track values > Integer.MAX_VALUE");
  }

  @Test
  void testClearDoesNotTruncateToIntRange() {
    // This test specifically validates the bug fix:
    // clear() should set min = Long.MAX_VALUE, not Integer.MAX_VALUE

    minimum.add(100);
    minimum.clear();

    // Add a value between Integer.MAX_VALUE and Long.MAX_VALUE
    long valueInLongRange = (long) Integer.MAX_VALUE + 1000L;
    minimum.add(valueInLongRange);

    // If clear() incorrectly used Integer.MAX_VALUE, this comparison would fail
    // because Integer.MAX_VALUE < valueInLongRange
    assertEquals(valueInLongRange, minimum.getValue(),
        "After clear(), minimum should use Long.MAX_VALUE not Integer.MAX_VALUE");
  }

  @Test
  void testGetData() {
    minimum.add(42);

    Object data = minimum.getData();
    assertInstanceOf(Long.class, data, "getData should return Long");
    assertEquals(42L, data, "getData should return same as getValue");
  }

  @Test
  void testZero() {
    minimum.add(0);
    minimum.add(100);
    minimum.add(-100);

    assertEquals(-100L, minimum.getValue(), "Minimum should correctly handle zero");
  }

  @Test
  void testSingleValue() {
    minimum.add(42);
    assertEquals(42L, minimum.getValue(), "Minimum of single value should be that value");
  }

  @Test
  void testLongMinValue() {
    minimum.add(Long.MIN_VALUE);
    minimum.add(0);
    minimum.add(Long.MAX_VALUE);

    assertEquals(Long.MIN_VALUE, minimum.getValue(),
        "Minimum should correctly handle Long.MIN_VALUE");
  }
}
