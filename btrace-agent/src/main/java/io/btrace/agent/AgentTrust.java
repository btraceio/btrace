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
package io.btrace.agent;

import static io.btrace.core.Args.PROBE_MAX_RATE;
import static io.btrace.core.Args.PROBE_TTL;

import io.btrace.core.SharedSettings;

/**
 * How the agent's arguments turn trusted mode on, and what the agent says when they cannot: under a
 * probe budget the operator's launch-time grant is the only one that counts.
 */
final class AgentTrust {
  static final String TRUSTED_PROPERTY = "btrace.trusted";

  private AgentTrust() {}

  /**
   * Trusted mode is enabled only by the {@code trusted=true} agent argument. A {@code
   * -Dbtrace.trusted} system property looks like the same switch and is read by nothing, so name
   * the argument that works instead of leaving a security-relevant setting silently ignored. One
   * agent load at a time initializes an agent in a target JVM, and a later load is handed over to
   * the agent already there, so this is reported once unless a failed first load is retried.
   *
   * @param budget whether a probe budget is requested for this JVM; the client's {@code -u} flag is
   *     then refused, so the warning does not recommend it
   * @return the warning, or {@code null} when the property is unset or trusted mode is on anyway
   */
  static String ineffectiveTrustedPropertyWarning(
      String propertyValue, boolean trusted, boolean budget) {
    if (propertyValue == null || trusted) {
      return null;
    }
    return "Ignoring system property "
        + TRUSTED_PROPERTY
        + "="
        + propertyValue
        + ": it has no effect. Trusted mode is enabled only by the trusted=true agent argument "
        + trustedHowTo(budget)
        + ".";
  }

  /** Where the {@code trusted=true} agent argument can come from, with or without a budget. */
  static String trustedHowTo(boolean budget) {
    return budget
        ? "(-javaagent:btrace.jar=trusted=true,... at JVM launch; this JVM has a probe budget, so"
            + " the client's -u flag is refused)"
        : "(-javaagent:btrace.jar=trusted=true,... or the client's -u flag)";
  }

  // Whether the operator set a budget property, valid or not; read before arguments are parsed.
  private static volatile boolean budgetPropertySet;

  /** Whether a probe budget is requested, by property or by a launch-time agent argument. */
  static boolean budgetRequested() {
    if (budgetPropertySet) {
      return true;
    }
    return "premain".equals(Main.agentMode)
        && (Main.argMap.get(PROBE_TTL) != null || Main.argMap.get(PROBE_MAX_RATE) != null);
  }

  // Whether trusted=true in the agent arguments was refused because of a probe budget. Not
  // private: the tests of this package clear it between cases.
  static volatile boolean trustRequestedAndRefused;

  // Whether attach-time agent arguments cannot turn trusted mode on: set before they are parsed.
  private static volatile boolean attachTrustBarred;

  /**
   * Applies the {@code trusted} agent argument. Under dynamic attach with an operator budget it is
   * not applied, whatever its value. A request to turn it on is recorded so it can be reported.
   */
  static void applyTrustedArgument(boolean trusted) {
    applyTrustedArgument(SharedSettings.GLOBAL, trusted, attachTrustBarred);
  }

  static void applyTrustedArgument(SharedSettings agentSettings, boolean trusted, boolean barred) {
    if (barred) {
      if (trusted) {
        trustRequestedAndRefused = true;
      }
      return;
    }
    agentSettings.setTrusted(trusted);
  }

  static boolean isTrustRequestedAndRefused() {
    return trustRequestedAndRefused;
  }

  /**
   * Called when an agent load starts, before its arguments are parsed.
   *
   * @param trustBarred whether the arguments of this load cannot turn trusted mode on
   * @param budgetProperty whether the operator set a budget property, valid or not
   */
  static void loadStarted(boolean trustBarred, boolean budgetProperty) {
    attachTrustBarred = trustBarred;
    budgetPropertySet = budgetProperty;
  }
}
