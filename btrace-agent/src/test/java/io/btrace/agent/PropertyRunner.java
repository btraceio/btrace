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

import java.util.Random;
import java.util.function.Function;

/**
 * Minimal seeded property runner. Runs a property over generated inputs; a failure reports the
 * seed, the iteration and the input so the run can be replayed with {@code -Dbtrace.prop.seed} and
 * {@code -Dbtrace.prop.iterations}.
 */
final class PropertyRunner {
  static final long DEFAULT_SEED = 0xB74ACE5EEDL;
  static final int DEFAULT_ITERATIONS = 200;

  /** A property over one generated input; throws (typically an assertion error) to fail. */
  interface Property<T> {
    void check(T input) throws Exception;
  }

  private PropertyRunner() {}

  static long seed() {
    return Long.getLong("btrace.prop.seed", DEFAULT_SEED);
  }

  static int iterations() {
    return Integer.getInteger("btrace.prop.iterations", DEFAULT_ITERATIONS);
  }

  static <T> void forAll(String name, Function<Random, T> generator, Property<T> property) {
    long seed = seed();
    int iterations = iterations();
    Random rnd = new Random(seed);
    for (int i = 0; i < iterations; i++) {
      T input = generator.apply(rnd);
      try {
        property.check(input);
      } catch (Throwable t) {
        throw new AssertionError(
            name
                + " failed: seed="
                + seed
                + " iteration="
                + i
                + " input="
                + describe(input)
                + " cause="
                + t,
            t);
      }
    }
  }

  /** Renders the input with control and non-ASCII characters escaped so logs stay readable. */
  static String describe(Object input) {
    String s = String.valueOf(input);
    StringBuilder sb = new StringBuilder(s.length() + 2).append('<');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20 || c > 0x7e) {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.append('>').toString();
  }
}
