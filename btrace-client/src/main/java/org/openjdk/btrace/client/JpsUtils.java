package org.openjdk.btrace.client;

import org.openjdk.btrace.core.DebugSupport;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

import java.util.ArrayList;
import java.util.Collection;

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
                MonitoredVm vm = vmHost.getMonitoredVm(id);
                if (MonitoredVmUtil.isAttachable(vm)) {
                    String mainClass = MonitoredVmUtil.mainClass(vm, false);
                    vms.add(vmPid + " " + mainClass);
                }
            }
        } catch (Exception e) {
            DebugSupport.warning(e);
        }
        return vms;
    }
}
