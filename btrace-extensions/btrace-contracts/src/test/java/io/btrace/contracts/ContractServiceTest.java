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
package io.btrace.contracts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractServiceTest {

  private ContractServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ContractServiceImpl();
  }

  // ==================== Latency checks ====================

  @Test
  void latencyWithinBudget() {
    service.checkLatency("api.generate", 100_000_000L, 500_000_000L);
    assertEquals(1, service.getTotalChecks());
    assertEquals(0, service.getTotalViolations());
    assertFalse(service.hasViolations());
  }

  @Test
  void latencyExceedsBudget() {
    service.checkLatency("api.generate", 600_000_000L, 500_000_000L);
    assertEquals(1, service.getTotalViolations());
    assertTrue(service.hasViolations());
    String summary = service.getSummary();
    assertTrue(summary.contains("VIOLATIONS: 1"));
    assertTrue(summary.contains("exceeded budget"));
  }

  @Test
  void latencyStatsTracked() {
    service.checkLatency("api.call", 100_000_000L, 1_000_000_000L);
    service.checkLatency("api.call", 200_000_000L, 1_000_000_000L);
    service.checkLatency("api.call", 300_000_000L, 1_000_000_000L);

    assertEquals(3, service.getTotalChecks());
    String summary = service.getSummary();
    assertTrue(summary.contains("avg 200ms"));
    assertTrue(summary.contains("min 100ms"));
    assertTrue(summary.contains("max 300ms"));
  }

  // ==================== Call rate checks ====================

  @Test
  void callRateWithinLimit() {
    service.checkCallRate("api.query", 1000);
    assertEquals(0, service.getTotalViolations());
  }

  @Test
  void callRateExceedsLimit() {
    for (int i = 0; i < 200; i++) {
      service.checkCallRate("api.query", 10);
    }
    assertTrue(service.getTotalViolations() > 0);
  }

  // ==================== Condition assertions ====================

  @Test
  void assertConditionTrue() {
    service.assertCondition("positive-balance", true, "Balance must be positive");
    assertEquals(0, service.getTotalViolations());
  }

  @Test
  void assertConditionFalse() {
    service.assertCondition("positive-balance", false, "Balance must be positive");
    assertEquals(1, service.getTotalViolations());
    assertTrue(service.getSummary().contains("Balance must be positive"));
  }

  // ==================== Range checks ====================

  @Test
  void rangeWithinBounds() {
    service.checkRange("response-code", 200, 100, 599);
    assertEquals(0, service.getTotalViolations());
  }

  @Test
  void rangeBelowMin() {
    service.checkRange("response-code", 50, 100, 599);
    assertEquals(1, service.getTotalViolations());
    assertTrue(service.getSummary().contains("outside range"));
  }

  @Test
  void rangeAboveMax() {
    service.checkRange("response-code", 700, 100, 599);
    assertEquals(1, service.getTotalViolations());
  }

  @Test
  void rangeAtBoundaries() {
    service.checkRange("val", 100, 100, 200);
    service.checkRange("val", 200, 100, 200);
    assertEquals(0, service.getTotalViolations());
  }

  // ==================== Null checks ====================

  @Test
  void checkNotNullWithValue() {
    service.checkNotNull("api.result", "hello");
    assertEquals(0, service.getTotalViolations());
  }

  @Test
  void checkNotNullWithNull() {
    service.checkNotNull("api.result", null);
    assertEquals(1, service.getTotalViolations());
    assertTrue(service.getSummary().contains("Unexpected null"));
  }

  // ==================== Code path tracking ====================

  @Test
  void twoTagComparison() {
    service.trackCodePath("Parser.parse", 50_000_000L, "v2");
    service.trackCodePath("Parser.parse", 60_000_000L, "v2");
    service.trackCodePath("Parser.parse", 30_000_000L, "v1");
    service.trackCodePath("Parser.parse", 40_000_000L, "v1");

    String summary = service.getSummary();
    assertTrue(summary.contains("Tracked Code Paths"));
    assertTrue(summary.contains("v1"));
    assertTrue(summary.contains("v2"));
    assertTrue(summary.contains("2 calls"));
    // v2 avg 55ms, v1 avg 35ms -> v2 ~57% slower than v1 (alphabetical: v1 first, v2 second)
    assertTrue(summary.contains("slower") || summary.contains("faster"));
  }

  @Test
  void singleTagTracking() {
    service.trackCodePath("Renderer.render", 100_000_000L, "cached");
    String summary = service.getSummary();
    assertTrue(summary.contains("Tracked Code Paths"));
    assertTrue(summary.contains("cached"));
    assertTrue(summary.contains("1 calls"));
  }

  @Test
  void multipleTagsNoComparison() {
    service.trackCodePath("Op.run", 10_000_000L, "a");
    service.trackCodePath("Op.run", 20_000_000L, "b");
    service.trackCodePath("Op.run", 30_000_000L, "c");

    String summary = service.getSummary();
    assertTrue(summary.contains("a"));
    assertTrue(summary.contains("b"));
    assertTrue(summary.contains("c"));
    // More than 2 tags: no slower/faster comparison
    assertFalse(summary.contains("slower") || summary.contains("faster"),
        "Cross-comparison should only appear for exactly 2 tags");
  }

  // ==================== Reporting ====================

  @Test
  void noDataSummary() {
    assertEquals("No contracts checked.", service.getSummary());
  }

  @Test
  void getViolationsPerContract() {
    service.assertCondition("a", false, "fail");
    service.assertCondition("a", false, "fail");
    service.assertCondition("b", false, "fail");

    assertEquals(2, service.getViolations("a"));
    assertEquals(1, service.getViolations("b"));
    assertEquals(0, service.getViolations("c"));
  }

  @Test
  void multipleContractsInSummary() {
    service.checkLatency("fast-api", 10_000_000L, 100_000_000L);
    service.checkLatency("slow-api", 500_000_000L, 100_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("fast-api"));
    assertTrue(summary.contains("slow-api"));
    assertTrue(summary.contains("Checks: 2"));
    assertTrue(summary.contains("Violations: 1"));
  }

  @Test
  void allContractsSatisfied() {
    service.checkLatency("api", 10_000_000L, 100_000_000L);
    service.assertCondition("invariant", true, "ok");
    service.checkRange("val", 50, 0, 100);

    String summary = service.getSummary();
    assertTrue(summary.contains("all contracts satisfied"));
  }

  @Test
  void reset() {
    service.assertCondition("a", false, "fail");
    service.trackCodePath("b", 100L, "tag");
    service.reset();

    assertEquals(0, service.getTotalChecks());
    assertEquals(0, service.getTotalViolations());
    assertFalse(service.hasViolations());
    assertEquals("No contracts checked.", service.getSummary());
  }

  @Test
  void concurrentChecks() throws Exception {
    int threads = 8;
    int checksPerThread = 1000;
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(
              () -> {
                try {
                  for (int i = 0; i < checksPerThread; i++) {
                    service.checkLatency("concurrent-api", 50_000_000L, 100_000_000L);
                  }
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();

    assertEquals(threads * checksPerThread, service.getTotalChecks());
    assertEquals(0, service.getTotalViolations());
  }
}
