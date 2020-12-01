package org.openjdk.btrace.client;

import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.openjdk.btrace.core.DebugSupport;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

final class JpsUtils {
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
        DebugSupport.warning(e);
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
              "["
                  + (hasBTraceServer(vmPid) ? "+" : "-")
                  + "] "
                  + vmPid
                  + " "
                  + mainClass
                  + " ["
                  + MonitoredVmUtil.commandLine(mvm)
                  + "]");
        }
      }
    } catch (Exception e) {
      DebugSupport.warning(e);
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
