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
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

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
      return findVmByRegex(name.substring(REGEX_PREFIX.length()), activeVms(), selfPid(), chooser);
    }
    try {
      return Integer.parseInt(name);
    } catch (NumberFormatException ignored) {
      Integer pid = null;
      try {
        MonitoredHost vmHost = MonitoredHost.getMonitoredHost((String) null);
        for (Integer vmPid : MonitoredHost.getMonitoredHost("localhost").activeVms()) {
          VmIdentifier id = new VmIdentifier(vmPid.toString());
          MonitoredVm vm = vmHost.getMonitoredVm(id);
          String mainClass = MonitoredVmUtil.mainClass(vm, false);
          if (name.equalsIgnoreCase(mainClass)) {
            pid = vmPid;
            break;
          }
        }
      } catch (Exception e) {
        log.warn("Unexpected exception", e);
      }
      return pid;
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

  private static List<VmInfo> activeVms() {
    List<VmInfo> vms = new ArrayList<>();
    try {
      MonitoredHost vmHost = MonitoredHost.getMonitoredHost((String) null);
      for (Integer vmPid : MonitoredHost.getMonitoredHost("localhost").activeVms()) {
        try {
          MonitoredVm vm = vmHost.getMonitoredVm(new VmIdentifier(vmPid.toString()));
          String mainClass = MonitoredVmUtil.mainClass(vm, false);
          String commandLine = MonitoredVmUtil.commandLine(vm);
          if (commandLine == null || commandLine.isEmpty() || "Unknown".equals(commandLine)) {
            commandLine = mainClass;
          }
          vms.add(new VmInfo(vmPid, mainClass, commandLine));
        } catch (Exception e) {
          // the JVM may have exited or be inaccessible to this user
          log.debug("Skipping JVM {}", vmPid, e);
        }
      }
    } catch (Exception e) {
      log.warn("Unexpected exception", e);
    }
    return vms;
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
    try {
      MonitoredHost vmHost = MonitoredHost.getMonitoredHost((String) null);
      for (Integer vmPid : MonitoredHost.getMonitoredHost("localhost").activeVms()) {
        VmIdentifier id = new VmIdentifier(vmPid.toString());
        MonitoredVm mvm = vmHost.getMonitoredVm(id);
        if (MonitoredVmUtil.isAttachable(mvm)) {
          String mainClass = MonitoredVmUtil.mainClass(mvm, false);

          vms.add(
              "("
                  + (hasBTraceServer(vmPid) ? "+" : "-")
                  + ") "
                  + vmPid
                  + " "
                  + mainClass
                  + " ["
                  + MonitoredVmUtil.commandLine(mvm)
                  + "]");
        }
      }
    } catch (Exception e) {
      log.warn("Unexpected exception", e);
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
