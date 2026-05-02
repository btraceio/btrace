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
package org.openjdk.btrace.core.aggregation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaximumTest {

  private Maximum maximum;

  @BeforeEach
  void setUp() {
    maximum = new Maximum();
  }

  @Test
  void testInitialValue() {
    assertEquals(Long.MIN_VALUE, maximum.getValue(), "Initial maximum should be Long.MIN_VALUE");
  }

  @Test
  void testSimpleMaximum() {
    maximum.add(10);
    maximum.add(30);
    maximum.add(20);
    assertEquals(30L, maximum.getValue(), "Maximum of 10, 30, 20 should be 30");
  }

  @Test
  void testNegativeValues() {
    maximum.add(-50);
    maximum.add(-10);
    maximum.add(-100);
    assertEquals(-10L, maximum.getValue(), "Maximum should handle negative values");
  }

  @Test
  void testMixedValues() {
    maximum.add(-50);
    maximum.add(100);
    maximum.add(0);
    assertEquals(100L, maximum.getValue(), "Maximum of mixed values should be 100");
  }

  @Test
  void testLargeValues() {
    long largeValue1 = Long.MAX_VALUE - 100;
    long largeValue2 = Long.MAX_VALUE - 50;
    maximum.add(largeValue1);
    maximum.add(largeValue2);

    assertEquals(largeValue2, maximum.getValue(), "Maximum should handle large values correctly");
  }

  @Test
  void testValuesExceedingIntegerRange() {
    // Test values > Integer.MAX_VALUE
    long value1 = 3_000_000_000L; // > Integer.MAX_VALUE
    long value2 = 4_000_000_000L;

    maximum.add(value1);
    maximum.add(value2);

    assertEquals(
        value2,
        maximum.getValue(),
        "Maximum should work correctly with values > Integer.MAX_VALUE");
  }

  @Test
  void testClearResetsToLongMinValue() {
    maximum.add(100);
    maximum.add(200);
    assertEquals(200L, maximum.getValue());

    maximum.clear();
    assertEquals(
        Long.MIN_VALUE,
        maximum.getValue(),
        "After clear(), maximum should reset to Long.MIN_VALUE");
  }

  @Test
  void testClearThenAddLargeValue() {
    maximum.add(100);
    maximum.clear();

    // Add a value > Integer.MAX_VALUE
    long largeValue = 3_000_000_000L;
    maximum.add(largeValue);

    assertEquals(
        largeValue,
        maximum.getValue(),
        "After clear(), should correctly track values > Integer.MAX_VALUE");
  }

  @Test
  void testClearDoesNotTruncateToIntRange() {
    // This test specifically validates the bug fix:
    // clear() should set max = Long.MIN_VALUE, not Integer.MIN_VALUE

    maximum.add(-1000);
    maximum.clear();

    // Add a value between Integer.MIN_VALUE and Long.MIN_VALUE
    long valueInLongRange = (long) Integer.MIN_VALUE - 1000L;
    maximum.add(valueInLongRange);

    // If clear() incorrectly used Integer.MIN_VALUE, this comparison would fail
    // because Integer.MIN_VALUE > valueInLongRange
    assertEquals(
        valueInLongRange,
        maximum.getValue(),
        "After clear(), maximum should use Long.MIN_VALUE not Integer.MIN_VALUE");
  }

  @Test
  void testGetData() {
    maximum.add(42);

    Object data = maximum.getData();
    assertInstanceOf(Long.class, data, "getData should return Long");
    assertEquals(42L, data, "getData should return same as getValue");
  }

  @Test
  void testZero() {
    maximum.add(0);
    maximum.add(100);
    maximum.add(-100);

    assertEquals(100L, maximum.getValue(), "Maximum should correctly handle zero");
  }

  @Test
  void testSingleValue() {
    maximum.add(42);
    assertEquals(42L, maximum.getValue(), "Maximum of single value should be that value");
  }

  @Test
  void testLongMaxValue() {
    maximum.add(Long.MIN_VALUE);
    maximum.add(0);
    maximum.add(Long.MAX_VALUE);

    assertEquals(
        Long.MAX_VALUE, maximum.getValue(), "Maximum should correctly handle Long.MAX_VALUE");
  }
}
