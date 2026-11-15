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

import io.btrace.core.Args;
import io.btrace.core.ArgsMap;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * The effective probe budget limits: a time-to-live and a hit-rate ceiling, either of which may be
 * unset (0). The operator sets them with the {@code io.btrace.probeTtl} and {@code
 * io.btrace.probeMaxRate} system properties; the {@code probeTtl} and {@code probeMaxRate} agent
 * arguments count only when the agent is loaded at JVM launch, where they can tighten the
 * properties but never loosen them. Under dynamic attach they are ignored. The limits resolved at
 * launch are written back to the two properties, so an agent loaded into the same JVM later is
 * bound by them too.
 */
final class BudgetLimits {
  static final String TTL_PROPERTY = "io.btrace.probeTtl";
  static final String MAX_RATE_PROPERTY = "io.btrace.probeMaxRate";

  static final BudgetLimits NONE = new BudgetLimits(0, 0);

  private final long ttlSeconds;
  private final long maxRatePerSecond;

  private BudgetLimits(long ttlSeconds, long maxRatePerSecond) {
    this.ttlSeconds = ttlSeconds;
    this.maxRatePerSecond = maxRatePerSecond;
  }

  /** Time-to-live per probe in seconds; 0 means no limit. */
  long ttlSeconds() {
    return ttlSeconds;
  }

  /**
   * Maximum executions per second of one probe's handlers attached to application code; 0 means no
   * limit. Handlers the runtime calls itself (timers, events) are not counted.
   */
  long maxRatePerSecond() {
    return maxRatePerSecond;
  }

  boolean any() {
    return ttlSeconds > 0 || maxRatePerSecond > 0;
  }

  /** The line a client sees when its trusted-mode deploy is refused because a budget is set. */
  static final String TRUSTED_REFUSED =
      "trusted mode is not available: this JVM has a probe budget set by its operator";

  /**
   * Whether trusted mode must be refused to clients. A trusted probe is unverified code that can
   * defeat the budget, so under a budget only the operator can grant it: {@code trusted=true} in
   * agent arguments written by the operator (premain).
   *
   * @param premain true when agent arguments were written by the operator
   * @param argumentTrusted true when the agent arguments turned trusted mode on
   */
  static boolean refusesClientTrust(BudgetLimits limits, boolean premain, boolean argumentTrusted) {
    return limits.any() && !(premain && argumentTrusted);
  }

  /** The raw operator property values, captured before agent arguments are parsed. */
  static final class Snapshot {
    private final String ttl;
    private final String rate;

    private Snapshot(String ttl, String rate) {
      this.ttl = ttl;
      this.rate = rate;
    }

    /** Whether the operator set either property, valid or not. */
    boolean present() {
      return ttl != null || rate != null;
    }
  }

  /** Reads the two operator properties. Call before agent arguments are parsed. */
  static Snapshot snapshot(Properties systemProperties) {
    return new Snapshot(
        systemProperties.getProperty(TTL_PROPERTY),
        systemProperties.getProperty(MAX_RATE_PROPERTY));
  }

  /**
   * Combines the operator properties with the agent arguments.
   *
   * @param premain true when agent arguments were written by the operator
   * @param report receives one line for each budget argument that is ignored (attach mode)
   * @throws InvalidAgentOptionException for an invalid operator value (any system property; any
   *     agent argument when premain)
   */
  static BudgetLimits resolve(
      Snapshot operator, ArgsMap args, boolean premain, Consumer<String> report)
      throws InvalidAgentOptionException {
    long ttl =
        combine(parse(TTL_PROPERTY, operator.ttl), argument(Args.PROBE_TTL, args, premain, report));
    long rate =
        combine(
            parse(MAX_RATE_PROPERTY, operator.rate),
            argument(Args.PROBE_MAX_RATE, args, premain, report));
    return ttl == 0 && rate == 0 ? NONE : new BudgetLimits(ttl, rate);
  }

  /**
   * Writes the limits resolved at launch to the operator properties. Every agent load gets its own
   * agent instance, and a later one reads only these properties: without this, a budget set by
   * launch-time agent arguments would not bind an agent that an attacher loads afterwards.
   */
  static void publish(BudgetLimits limits, Properties systemProperties) {
    if (limits.ttlSeconds > 0) {
      systemProperties.setProperty(TTL_PROPERTY, Long.toString(limits.ttlSeconds));
    }
    if (limits.maxRatePerSecond > 0) {
      systemProperties.setProperty(MAX_RATE_PROPERTY, Long.toString(limits.maxRatePerSecond));
    }
  }

  /**
   * Writes an invalid launch-time budget argument to its operator property, so a later agent load
   * fails on it the same way instead of running without a budget.
   */
  static void publishInvalid(InvalidAgentOptionException invalid, Properties systemProperties) {
    if (Args.PROBE_TTL.equals(invalid.getOption())) {
      systemProperties.setProperty(TTL_PROPERTY, invalid.getValue());
    } else if (Args.PROBE_MAX_RATE.equals(invalid.getOption())) {
      systemProperties.setProperty(MAX_RATE_PROPERTY, invalid.getValue());
    }
  }

  private static long argument(String name, ArgsMap args, boolean premain, Consumer<String> report)
      throws InvalidAgentOptionException {
    String raw = args.get(name);
    if (!premain) {
      // The attacher is not the operator: it can neither set nor tighten a budget, which would
      // otherwise hold for every later deploy until the JVM restarts.
      if (raw != null) {
        report.accept(
            "ignoring agent argument "
                + name
                + "="
                + raw
                + ": a probe budget can only be set by the operator, at JVM launch");
      }
      return 0;
    }
    return parse(name, raw);
  }

  /** The smaller of two limits, where 0 means unset. */
  private static long combine(long ceiling, long requested) {
    if (ceiling == 0) {
      return requested;
    }
    return requested == 0 ? ceiling : Math.min(ceiling, requested);
  }

  private static long parse(String option, String raw) throws InvalidAgentOptionException {
    if (raw == null) {
      return 0;
    }
    try {
      long value = Long.parseLong(raw.trim());
      if (value > 0) {
        return value;
      }
    } catch (NumberFormatException ignored) {
      // reported below
    }
    throw new InvalidAgentOptionException(option, raw, "expected a positive whole number");
  }
}
