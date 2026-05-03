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
package org.openjdk.btrace.extension.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BTraceVersionRangeTest {

  // --- range-format requirements (X.Y+) ---

  @Test
  void exactMajorMinorRange_satisfiedByEqualVersion() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("3.0.0"));
  }

  @Test
  void exactMajorMinorRange_satisfiedByHigherPatch() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("3.0.5"));
  }

  @Test
  void exactMajorMinorRange_satisfiedByHigherMinor() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("3.1.0"));
  }

  @Test
  void exactMajorMinorRange_satisfiedByHigherMajor() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("4.0.0"));
  }

  @Test
  void exactMajorMinorRange_notSatisfiedByLowerMinor() {
    assertFalse(BTraceVersionRange.parse("3.1+").satisfiedBy("3.0.9"));
  }

  @Test
  void exactMajorMinorRange_notSatisfiedByLowerMajor() {
    assertFalse(BTraceVersionRange.parse("3.0+").satisfiedBy("2.9.9"));
  }

  // --- plain version requirements (no +) ---

  @Test
  void plainVersion_treatedAsMinimum() {
    assertTrue(BTraceVersionRange.parse("3.0.0").satisfiedBy("3.0.0"));
  }

  @Test
  void plainVersion_satisfiedByHigherPatch() {
    assertTrue(BTraceVersionRange.parse("3.0.0").satisfiedBy("3.0.1"));
  }

  @Test
  void plainVersion_notSatisfiedByLower() {
    assertFalse(BTraceVersionRange.parse("3.1.0").satisfiedBy("3.0.9"));
  }

  // --- qualifier stripping in actual version ---

  @Test
  void snapshotQualifier_stripped() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("3.0.0-SNAPSHOT"));
  }

  @Test
  void rcQualifier_stripped() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("3.1.0-RC1"));
  }

  // --- unknown / empty actual version bypasses check ---

  @Test
  void unknownActualVersion_alwaysSatisfies() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy("unknown"));
  }

  @Test
  void emptyActualVersion_alwaysSatisfies() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy(""));
  }

  @Test
  void nullActualVersion_alwaysSatisfies() {
    assertTrue(BTraceVersionRange.parse("3.0+").satisfiedBy(null));
  }

  // --- empty / null requirement imposes no constraint ---

  @Test
  void emptyRequirement_alwaysSatisfied() {
    assertTrue(BTraceVersionRange.parse("").satisfiedBy("2.0.0"));
  }

  @Test
  void nullRequirement_alwaysSatisfied() {
    assertTrue(BTraceVersionRange.parse(null).satisfiedBy("2.0.0"));
  }

  // --- three-part range (X.Y.Z+) ---

  @Test
  void threePartRange_satisfiedByEqualVersion() {
    assertTrue(BTraceVersionRange.parse("3.0.1+").satisfiedBy("3.0.1"));
  }

  @Test
  void threePartRange_notSatisfiedBySmallerPatch() {
    assertFalse(BTraceVersionRange.parse("3.0.1+").satisfiedBy("3.0.0"));
  }
}
