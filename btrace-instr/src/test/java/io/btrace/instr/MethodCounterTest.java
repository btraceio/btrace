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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jbachorik
 */
public class MethodCounterTest {

  public MethodCounterTest() {}

  @BeforeAll
  public static void setUpClass() {}

  @AfterAll
  public static void tearDownClass() {}

  @BeforeEach
  public void setUp() {}

  @AfterEach
  public void tearDown() {}

  /** Test of hit method, of class MethodCounter. */
  @Test
  public void testHit() {
    System.out.println("hit");
    final int iterations = 3000000;
    final int mean = 20;

    int methodId = 0;

    MethodTracker.registerCounter(methodId, mean);

    int hits = 0;

    for (int i = 0; i < iterations; i++) {
      hits += MethodTracker.hit(methodId) ? 1 : 0;
    }
    long b = System.nanoTime();

    System.err.println("hits = " + hits);

    assertTrue(Math.abs(mean - (iterations / hits)) < (mean / 10));
  }
}
