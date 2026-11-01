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
package io.btrace.client;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

/**
 * Lists local JVMs through jvmstat, which also sees JVMs that cannot be attached to. All jvmstat
 * references live in this class: when {@code jdk.internal.jvmstat} is not exported to BTrace (the
 * client was started without the launcher script and without the jar manifest), using it throws an
 * {@link IllegalAccessError} that {@link JpsUtils} catches to fall back to the Attach API.
 */
final class JvmstatVms {
  private static final Logger log = LoggerFactory.getLogger(JvmstatVms.class);

  private JvmstatVms() {}

  static List<JpsUtils.VmInfo> list(boolean attachableOnly) throws Exception {
    List<JpsUtils.VmInfo> vms = new ArrayList<>();
    MonitoredHost vmHost = MonitoredHost.getMonitoredHost((String) null);
    for (Integer vmPid : MonitoredHost.getMonitoredHost("localhost").activeVms()) {
      try {
        MonitoredVm vm = vmHost.getMonitoredVm(new VmIdentifier(vmPid.toString()));
        if (attachableOnly && !MonitoredVmUtil.isAttachable(vm)) {
          continue;
        }
        String mainClass = MonitoredVmUtil.mainClass(vm, false);
        String commandLine = MonitoredVmUtil.commandLine(vm);
        if (commandLine == null || commandLine.isEmpty() || "Unknown".equals(commandLine)) {
          commandLine = mainClass;
        }
        vms.add(new JpsUtils.VmInfo(vmPid, mainClass, commandLine));
      } catch (Exception e) {
        // the JVM may have exited or be inaccessible to this user
        log.debug("Skipping JVM {}", vmPid, e);
      }
    }
    return vms;
  }
}
