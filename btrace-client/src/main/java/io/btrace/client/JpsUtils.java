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
package io.btrace.client;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JpsUtils {
  private static final Logger log = LoggerFactory.getLogger(JpsUtils.class);

  /** Prefix marking a target as a regular expression matched against the JVM command line. */
  static final String REGEX_PREFIX = "~";

  private static final String CLIENT_MAIN_CLASS = Main.class.getName();

  static final class VmInfo {
    final int pid;
    final String mainClass;
    final String commandLine;

    VmInfo(int pid, String mainClass, String commandLine) {
      this.pid = pid;
      this.mainClass = mainClass;
      this.commandLine = commandLine;
    }

    @Override
    public String toString() {
      return pid + " " + mainClass + " [" + commandLine + "]";
    }
  }

  /** Picks one of several JVMs matching a target. */
  interface VmChooser {
    /**
     * @return the chosen JVM or {@code null} to abort
     */
    VmInfo choose(String target, List<VmInfo> candidates);
  }

  /**
   * A chooser asking on the terminal; reading a line yields {@code null} at end of input.
   *
   * @param readLine shows the prompt and returns the answer
   * @param out where the candidates are listed
   */
  static VmChooser promptingChooser(Function<String, String> readLine, PrintWriter out) {
    return (target, candidates) -> {
      out.println("Pattern " + target + " matches " + candidates.size() + " JVMs:");
      for (int i = 0; i < candidates.size(); i++) {
        out.println("  [" + (i + 1) + "] " + candidates.get(i));
      }
      out.flush();
      while (true) {
        String answer =
            readLine.apply("Select JVM [1-" + candidates.size() + "] (empty or q to abort): ");
        if (answer == null) {
          return null;
        }
        answer = answer.trim();
        if (answer.isEmpty() || answer.equalsIgnoreCase("q")) {
          return null;
        }
        try {
          int idx = Integer.parseInt(answer);
          if (idx >= 1 && idx <= candidates.size()) {
            return candidates.get(idx - 1);
          }
        } catch (NumberFormatException ignored) {
        }
        out.println("Invalid choice: " + answer);
        out.flush();
      }
    };
  }

  static boolean isRegexTarget(String name) {
    return name.startsWith(REGEX_PREFIX);
  }

  /**
   * Resolves a target given as a PID, a main class name (exact, case-insensitive) or {@code
   * ~<regex>} (searched within the JVM command line, ie. main class or jar plus arguments).
   *
   * @param chooser asked to disambiguate when the regex matches more than one JVM; {@code null}
   *     makes an ambiguous match fail
   * @return the PID or {@code null} if no JVM matches
   * @throws IllegalArgumentException if the regex is invalid, or matches more than one JVM and the
   *     chooser is missing or picks none
   */
  static Integer findVmByName(String name, VmChooser chooser) {
    if (isRegexTarget(name)) {
      return findVmByRegex(
          name.substring(REGEX_PREFIX.length()), activeVms(false), selfPid(), chooser);
    }
    try {
      return Integer.parseInt(name);
    } catch (NumberFormatException ignored) {
      for (VmInfo vm : activeVms(false)) {
        if (name.equalsIgnoreCase(vm.mainClass)) {
          return vm.pid;
        }
      }
      return null;
    }
  }

  static Integer findVmByRegex(
      String regex, Collection<VmInfo> vms, int selfPid, VmChooser chooser) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(
          "Invalid JVM name pattern " + REGEX_PREFIX + regex + ": " + e.getDescription());
    }
    List<VmInfo> matches = new ArrayList<>();
    for (VmInfo vm : vms) {
      // the command lines of BTrace clients contain the pattern itself
      if (vm.pid == selfPid || vm.commandLine.startsWith(CLIENT_MAIN_CLASS)) {
        continue;
      }
      if (pattern.matcher(vm.commandLine).find()) {
        matches.add(vm);
      }
    }
    if (matches.isEmpty()) {
      return null;
    }
    if (matches.size() > 1) {
      if (chooser != null) {
        VmInfo chosen = chooser.choose(REGEX_PREFIX + regex, matches);
        if (chosen != null) {
          return chosen.pid;
        }
        throw new IllegalArgumentException("No JVM selected for pattern " + REGEX_PREFIX + regex);
      }
      StringBuilder msg =
          new StringBuilder("Pattern ")
              .append(REGEX_PREFIX)
              .append(regex)
              .append(" matches ")
              .append(matches.size())
              .append(
                  " JVMs; use a more specific pattern or a PID"
                      + " (or run interactively, without -x, to choose):");
      for (VmInfo vm : matches) {
        msg.append("\n  ").append(vm);
      }
      throw new IllegalArgumentException(msg.toString());
    }
    return matches.get(0).pid;
  }

  /** A way of listing the local JVMs. */
  interface VmSource {
    List<VmInfo> list(boolean attachableOnly) throws Exception;
  }

  private static List<VmInfo> activeVms(boolean attachableOnly) {
    return activeVms(JvmstatVms::list, JpsUtils::attachApiVms, attachableOnly);
  }

  /**
   * Lists JVMs with {@code primary} (jvmstat), falling back to {@code fallback} (the Attach API)
   * when it fails. jvmstat lives in the non-exported {@code jdk.internal.jvmstat} module, so it is
   * inaccessible when the client runs without the launcher script and outside {@code java -jar}
   * (e.g. from a {@code -cp} launch), and absent on runtimes without the module.
   */
  static List<VmInfo> activeVms(VmSource primary, VmSource fallback, boolean attachableOnly) {
    try {
      return primary.list(attachableOnly);
    } catch (LinkageError e) {
      log.debug("jvmstat is not accessible, listing JVMs through the Attach API: {}", e.toString());
    } catch (Exception e) {
      log.debug("jvmstat failed, listing JVMs through the Attach API", e);
    }
    try {
      return fallback.list(attachableOnly);
    } catch (Exception | LinkageError e) {
      log.warn("Unable to list local JVMs: {}", e.toString());
      return new ArrayList<>();
    }
  }

  /** Lists attachable JVMs through the public Attach API; it sees attachable JVMs only. */
  static List<VmInfo> attachApiVms(boolean attachableOnly) {
    List<VmInfo> vms = new ArrayList<>();
    for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
      VmInfo vm = fromDescriptor(vmd.id(), vmd.displayName());
      if (vm != null) {
        vms.add(vm);
      }
    }
    return vms;
  }

  /**
   * Builds the JVM info from an Attach API descriptor whose display name is the command line.
   *
   * @return {@code null} if the id is not a PID
   */
  static VmInfo fromDescriptor(String id, String displayName) {
    int pid;
    try {
      pid = Integer.parseInt(id);
    } catch (NumberFormatException e) {
      return null;
    }
    String commandLine = displayName == null ? "" : displayName.trim();
    String mainClass = mainClassOf(commandLine);
    if (commandLine.isEmpty()) {
      commandLine = mainClass;
    }
    return new VmInfo(pid, mainClass, commandLine);
  }

  /** Mirrors jvmstat's short main class name: the jar file name or the simple class name. */
  static String mainClassOf(String commandLine) {
    if (commandLine.isEmpty()) {
      return "Unknown";
    }
    int space = commandLine.indexOf(' ');
    String first = space < 0 ? commandLine : commandLine.substring(0, space);
    int slash = Math.max(first.lastIndexOf('/'), first.lastIndexOf('\\'));
    if (slash >= 0) {
      first = first.substring(slash + 1);
    }
    if (!first.endsWith(".jar")) {
      int dot = first.lastIndexOf('.');
      if (dot >= 0) {
        first = first.substring(dot + 1);
      }
    }
    return first;
  }

  private static int selfPid() {
    String name = ManagementFactory.getRuntimeMXBean().getName();
    try {
      return Integer.parseInt(name.substring(0, name.indexOf('@')));
    } catch (RuntimeException e) {
      return -1;
    }
  }

  static Collection<String> listVms() {
    Collection<String> vms = new ArrayList<>();
    for (VmInfo vm : activeVms(true)) {
      vms.add(
          "("
              + (hasBTraceServer(vm.pid) ? "+" : "-")
              + ") "
              + vm.pid
              + " "
              + vm.mainClass
              + " ["
              + vm.commandLine
              + "]");
    }
    return vms;
  }

  static boolean hasBTraceServer(int pid) {
    boolean result = false;
    VirtualMachine vm = null;
    try {
      vm = VirtualMachine.attach(String.valueOf(pid));
      result = vm.getSystemProperties().containsKey("btrace.port");
    } catch (Throwable ignored) {
    } finally {
      if (vm != null) {
        try {
          vm.detach();
        } catch (IOException ignored) {
        }
      }
    }
    return result;
  }
}
