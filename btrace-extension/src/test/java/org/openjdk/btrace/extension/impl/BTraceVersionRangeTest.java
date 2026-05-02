/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
