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
package resources;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/** Target process for the #888 runtime lifecycle integration test. */
public class Issue888RuntimeHardeningTarget {
  public static void main(String[] args) throws Exception {
    String processName = ManagementFactory.getRuntimeMXBean().getName();
    System.out.println("ready:" + processName.substring(0, processName.indexOf('@')));
    System.out.flush();
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    String command;
    while ((command = input.readLine()) != null) {
      if ("mbean-status".equals(command)) {
        reportMBean();
      } else if ("work".equals(command)) {
        work();
        System.out.println("work-done");
        System.out.flush();
      } else if ("done".equals(command)) {
        System.out.println("target-done");
        System.out.flush();
        return;
      }
    }
  }

  private static void work() {}

  private static void reportMBean() throws Exception {
    ObjectName name = new ObjectName("btrace:name=issue-888-runtime-hardening");
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    if (!server.isRegistered(name)) {
      System.out.println("mbean:absent");
    } else {
      MBeanInfo info = server.getMBeanInfo(name);
      boolean token = false;
      for (String field : info.getDescriptor().getFieldNames()) {
        Object value = info.getDescriptor().getFieldValue(field);
        if (field.startsWith("io.btrace.") && value != null && !value.toString().isEmpty()) {
          token = true;
        }
      }
      System.out.println("mbean:present:token=" + token);
    }
    System.out.flush();
  }
}
