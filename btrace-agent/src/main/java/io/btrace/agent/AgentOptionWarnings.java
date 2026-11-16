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

import io.btrace.core.Args;
import io.btrace.core.ArgsMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

/** Warnings about agent arguments that the agent accepts but does not act on. */
final class AgentOptionWarnings {
  /** Shorter names are too close to ordinary words to call a near miss a typo. */
  private static final int MIN_MISSPELLING_LENGTH = 5;

  private static final List<String> KNOWN_OPTIONS = knownOptions();

  private AgentOptionWarnings() {}

  /**
   * {@code grant=}, {@code deny=} and {@code grantAll=} are parsed for compatibility with earlier
   * 3.0 builds, but nothing consults them: extension linking is decided by {@code
   * allowExtensions=}, {@code denyExtensions=}, {@code allowPrivileged=} and the permission policy
   * file. Say so instead of letting the option silently do nothing.
   */
  static void ineffectivePermission(String option, Logger log) {
    log.warn(
        "{}= has no effect in BTrace 3.0; use allowExtensions=, denyExtensions= or"
            + " allowPrivileged= (or the permission policy file) to control extension linking",
        option);
  }

  /**
   * Logs one warning for each argument that looks like a misspelled agent option. Agent arguments
   * the agent does not know are legal: every script receives them as script arguments and every
   * extension configurator receives them too, so a name is reported only when it is almost
   * certainly a typo (see {@link #misspelledOptionWarning(String)}). The agent still starts.
   */
  static void misspelledOptions(ArgsMap args, Logger log) {
    for (Map.Entry<String, String> e : args) {
      String warning = misspelledOptionWarning(e.getKey());
      if (warning != null) {
        log.warn(warning);
      }
    }
  }

  /**
   * A name is a misspelled option when it is not an {@link Args} option, does not set a system
   * property ({@code $name}), is not a dotted, namespaced setting such as {@code otel.endpoint},
   * and either differs from an option only in letter case or, if at least {@value
   * #MIN_MISSPELLING_LENGTH} characters long, is one edit away from one (one character inserted,
   * removed or replaced, or two adjacent characters swapped, ignoring case).
   *
   * @return the warning, or {@code null} when the name is an option or not near one
   */
  static String misspelledOptionWarning(String name) {
    if (name == null
        || name.isEmpty()
        || name.startsWith("$")
        || name.indexOf('.') >= 0
        || KNOWN_OPTIONS.contains(name)) {
      return null;
    }
    String candidate = null;
    for (String option : KNOWN_OPTIONS) {
      if (option.equalsIgnoreCase(name)) {
        candidate = option;
        break;
      }
      if (candidate == null
          && name.length() >= MIN_MISSPELLING_LENGTH
          && withinOneEdit(name.toLowerCase(Locale.ROOT), option.toLowerCase(Locale.ROOT))) {
        candidate = option;
      }
    }
    if (candidate == null) {
      return null;
    }
    return "Unknown agent option '"
        + name
        + "': did you mean '"
        + candidate
        + "'? BTrace does not read it; it is only passed on to scripts and extension"
        + " configurators as an argument.";
  }

  /** The public String constants of {@link Args}, sorted so the suggestion is deterministic. */
  static List<String> knownOptions() {
    List<String> names = new ArrayList<>();
    for (Field f : Args.class.getFields()) {
      int m = f.getModifiers();
      if (Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == String.class) {
        try {
          names.add((String) f.get(null));
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot read agent option " + f.getName(), e);
        }
      }
    }
    Collections.sort(names);
    return Collections.unmodifiableList(names);
  }

  /** Optimal string alignment distance of at most one. */
  static boolean withinOneEdit(String a, String b) {
    if (a.equals(b)) {
      return true;
    }
    int la = a.length();
    int lb = b.length();
    if (Math.abs(la - lb) > 1) {
      return false;
    }
    int i = 0;
    while (i < Math.min(la, lb) && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    if (la == lb) {
      if (a.substring(i + 1).equals(b.substring(i + 1))) {
        return true; // one replacement
      }
      return i + 1 < la
          && a.charAt(i) == b.charAt(i + 1)
          && a.charAt(i + 1) == b.charAt(i)
          && a.substring(i + 2).equals(b.substring(i + 2));
    }
    return la > lb
        ? a.substring(i + 1).equals(b.substring(i))
        : a.substring(i).equals(b.substring(i + 1));
  }
}
