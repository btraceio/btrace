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
import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

final class JpsUtils {
  private static final Logger log = LoggerFactory.getLogger(JpsUtils.class);

  static Integer findVmByName(String name) {
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
