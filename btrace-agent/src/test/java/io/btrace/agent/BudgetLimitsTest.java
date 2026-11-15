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
    assertTrue(BudgetLimits.refusesClientTrust(limits, true, false));
  }

  @Test
  void budgetAllowsTrustGrantedByTheOperatorAtLaunch() throws Exception {
    BudgetLimits limits = resolve(props(null, "100"), new ArgsMap(), true, new ArrayList<>());
    assertFalse(BudgetLimits.refusesClientTrust(limits, true, true));
  }

  @Test
  void budgetRefusesTrustFromAttachArguments() throws Exception {
    BudgetLimits limits = resolve(props("60", null), new ArgsMap(), false, new ArrayList<>());
    assertTrue(BudgetLimits.refusesClientTrust(limits, false, true));
    assertTrue(BudgetLimits.refusesClientTrust(limits, false, false));
  }

  @Test
  void noBudgetNeverRefusesTrust() {
    for (boolean premain : new boolean[] {true, false}) {
      for (boolean trusted : new boolean[] {true, false}) {
        assertFalse(BudgetLimits.refusesClientTrust(BudgetLimits.NONE, premain, trusted));
      }
    }
  }

  @Test
  void launchLimitsArePublishedForLaterAgentLoads() throws Exception {
    Properties system = new Properties();
    Map<String, String> launch = new HashMap<>();
    launch.put("probeTtl", "30");
    launch.put("probeMaxRate", "500");
    ArgsMap args = new ArgsMap(launch);
    BudgetLimits limits =
        BudgetLimits.resolve(BudgetLimits.snapshot(system), args, true, line -> {});
    BudgetLimits.publish(limits, system);

    // A later agent load is in attach mode and sees only the properties.
    BudgetLimits.Snapshot later = BudgetLimits.snapshot(system);
    assertTrue(later.present());
    BudgetLimits inherited = BudgetLimits.resolve(later, new ArgsMap(), false, line -> {});
    assertEquals(30, inherited.ttlSeconds());
    assertEquals(500, inherited.maxRatePerSecond());
  }

  @Test
  void publishingNoLimitsSetsNoProperty() {
    Properties system = new Properties();
    BudgetLimits.publish(BudgetLimits.NONE, system);
    assertTrue(system.isEmpty());
    assertFalse(BudgetLimits.snapshot(system).present());
  }

  @Test
  void invalidLaunchArgumentIsPublishedSoALaterLoadFailsToo() {
    Properties system = new Properties();
    ArgsMap args = args("probeMaxRate", "lots");
    InvalidAgentOptionException invalid =
        assertThrows(
            InvalidAgentOptionException.class,
            () -> BudgetLimits.resolve(BudgetLimits.snapshot(system), args, true, line -> {}));
    BudgetLimits.publishInvalid(invalid, system);

    assertThrows(
        InvalidAgentOptionException.class,
        () ->
            BudgetLimits.resolve(BudgetLimits.snapshot(system), new ArgsMap(), false, line -> {}));
  }
}
