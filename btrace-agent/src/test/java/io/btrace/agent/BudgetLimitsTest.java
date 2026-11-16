/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BudgetLimitsTest {
  private static Properties props(String ttl, String rate) {
    Properties p = new Properties();
    if (ttl != null) {
      p.setProperty(BudgetLimits.TTL_PROPERTY, ttl);
    }
    if (rate != null) {
      p.setProperty(BudgetLimits.MAX_RATE_PROPERTY, rate);
    }
    return p;
  }

  private static ArgsMap args(String key, String value) {
    Map<String, String> m = new HashMap<>();
    m.put(key, value);
    return new ArgsMap(m);
  }

  private static BudgetLimits resolve(Properties p, ArgsMap a, boolean premain, List<String> out)
      throws InvalidAgentOptionException {
    return BudgetLimits.resolve(BudgetLimits.snapshot(p), a, premain, out::add);
  }

  @Test
  void unsetMeansNoLimit() throws Exception {
    BudgetLimits limits = resolve(new Properties(), new ArgsMap(), true, new ArrayList<>());
    assertFalse(limits.any());
    assertEquals(0, limits.ttlSeconds());
    assertEquals(0, limits.maxRatePerSecond());
  }

  @Test
  void propertyAloneIsTheCeiling() throws Exception {
    BudgetLimits limits = resolve(props(null, "1000"), new ArgsMap(), true, new ArrayList<>());
    assertEquals(1000, limits.maxRatePerSecond());
    assertTrue(limits.any());
  }

  @Test
  void argumentTightens() throws Exception {
    BudgetLimits limits =
        resolve(props(null, "1000"), args("probeMaxRate", "100"), true, new ArrayList<>());
    assertEquals(100, limits.maxRatePerSecond());
  }

  @Test
  void argumentCannotLoosen() throws Exception {
    BudgetLimits limits =
        resolve(props(null, "1000"), args("probeMaxRate", "5000"), true, new ArrayList<>());
    assertEquals(1000, limits.maxRatePerSecond());
  }

  @Test
  void argumentAloneApplies() throws Exception {
    BudgetLimits limits =
        resolve(new Properties(), args("probeTtl", "600"), true, new ArrayList<>());
    assertEquals(600, limits.ttlSeconds());
  }

  @Test
  void invalidPropertyThrows() {
    for (String bad : new String[] {"abc", "0", "-1"}) {
      InvalidAgentOptionException e =
          assertThrows(
              InvalidAgentOptionException.class,
              () -> resolve(props(bad, null), new ArgsMap(), true, new ArrayList<>()));
      assertTrue(e.getMessage().contains("io.btrace.probeTtl"), e.getMessage());
    }
  }

  @Test
  void invalidArgumentInPremainThrows() {
    InvalidAgentOptionException e =
        assertThrows(
            InvalidAgentOptionException.class,
            () -> resolve(new Properties(), args("probeMaxRate", "abc"), true, new ArrayList<>()));
    assertTrue(e.getMessage().contains("probeMaxRate=abc"), e.getMessage());
  }

  @Test
  void invalidArgumentUnderAttachIsReportedAndIgnored() throws Exception {
    List<String> reported = new ArrayList<>();
    BudgetLimits limits =
        resolve(props(null, "1000"), args("probeMaxRate", "abc"), false, reported);
    assertEquals(1000, limits.maxRatePerSecond());
    assertEquals(1, reported.size());
    assertTrue(reported.get(0).contains("probeMaxRate=abc"), reported.get(0));
  }

  @Test
  void argumentUnderAttachCannotSetOrTightenABudget() throws Exception {
    List<String> reported = new ArrayList<>();
    BudgetLimits none = resolve(new Properties(), args("probeTtl", "1"), false, reported);
    assertFalse(none.any(), "an attacher set a budget the operator did not");
    assertEquals(1, reported.size());
    assertTrue(reported.get(0).contains("probeTtl=1"), reported.get(0));

    BudgetLimits ceiling =
        resolve(props(null, "1000"), args("probeMaxRate", "100"), false, new ArrayList<>());
    assertEquals(1000, ceiling.maxRatePerSecond(), "an attacher changed the operator's ceiling");
  }

  @Test
  void budgetRefusesTrustRequestedByAClient() throws Exception {
    BudgetLimits limits = resolve(props(null, "100"), new ArgsMap(), true, new ArrayList<>());
    assertTrue(BudgetLimits.refusesClientTrust(limits, false));
  }

  @Test
  void budgetAllowsTrustGrantedByTheOperatorAtLaunch() throws Exception {
    BudgetLimits limits = resolve(props(null, "100"), new ArgsMap(), true, new ArrayList<>());
    assertFalse(BudgetLimits.refusesClientTrust(limits, true));
  }

  @Test
  void budgetResolvedUnderAttachRefusesTrustWithoutAGrant() throws Exception {
    BudgetLimits limits = resolve(props("60", null), new ArgsMap(), false, new ArrayList<>());
    assertTrue(BudgetLimits.refusesClientTrust(limits, false));
    assertFalse(BudgetLimits.refusesClientTrust(limits, true));
  }

  @Test
  void noBudgetNeverRefusesTrust() {
    for (boolean operatorGrant : new boolean[] {true, false}) {
      assertFalse(BudgetLimits.refusesClientTrust(BudgetLimits.NONE, operatorGrant));
    }
  }
}
