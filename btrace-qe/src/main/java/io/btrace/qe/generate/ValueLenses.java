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
package io.btrace.qe.generate;

import java.util.Random;

/**
 * Hostile-value lenses shared by the families. Every produced value is safe to pass as a process
 * argument and to write to YAML: no control characters. NUL cannot be passed on a command line (and
 * is not valid YAML), so the NUL lens uses U+FFFD, the Unicode replacement character, instead.
 */
final class ValueLenses {
  static final int COUNT = 9;

  private static final String[] INJECTED = {
    ",", "=", ":", "a,b=c:d", "=,=", ",,,", "x,debug=notabool", ":=:", "k=v,k=v"
  };

  private ValueLenses() {}

  /** Value for lens {@code lens} in {@code [0, COUNT)}; all variation comes from {@code rnd}. */
  static String value(int lens, Random rnd) {
    switch (lens) {
      case 0:
        return "";
      case 1:
        return repeat((char) ('a' + rnd.nextInt(26)), 10_000);
      case 2:
        return "-" + (1 + rnd.nextInt(100_000));
      case 3:
        return "9223372036854775808" + rnd.nextInt(1000);
      case 4:
        return word(rnd);
      case 5:
        return "é日本語😀" + word(rnd);
      case 6:
        return word(rnd) + "�" + word(rnd);
      case 7:
        return INJECTED[rnd.nextInt(INJECTED.length)];
      default:
        return " " + word(rnd) + " ";
    }
  }

  static String name(int lens) {
    switch (lens) {
      case 0:
        return "empty";
      case 1:
        return "10k-chars";
      case 2:
        return "negative";
      case 3:
        return "beyond-long-max";
      case 4:
        return "non-numeric";
      case 5:
        return "unicode";
      case 6:
        return "nul-replacement";
      case 7:
        return "injected-separators";
      default:
        return "padded";
    }
  }

  private static String word(Random rnd) {
    int len = 3 + rnd.nextInt(6);
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append((char) ('a' + rnd.nextInt(26)));
    }
    return sb.toString();
  }

  private static String repeat(char c, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb.toString();
  }
}
