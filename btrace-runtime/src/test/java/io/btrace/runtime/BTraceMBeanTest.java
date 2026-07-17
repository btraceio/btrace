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
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Property;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

class BTraceMBeanTest {
  @BTrace(name = "issue-888-runtime-mbean")
  static class PropertyProbe {
    @Property static long value = 7L;
  }

  @BTrace(name = "issue-888-runtime-no-mbean")
  static class NoPropertyProbe {}

  @Test
  void registrationIsOwnedAndIdempotentlyReleased() throws Exception {
    ObjectName name = new ObjectName("btrace:name=issue-888-runtime-mbean");
    if (ManagementFactory.getPlatformMBeanServer().isRegistered(name)) {
      ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
    }
    BTraceMBean.Registration registration = BTraceMBean.registerMBean(PropertyProbe.class);
    try {
      assertNotNull(registration);
      assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(name));
      assertNotNull(ManagementFactory.getPlatformMBeanServer().getMBeanInfo(name).getDescriptor());
      registration.close();
      registration.close();
      assertFalse(ManagementFactory.getPlatformMBeanServer().isRegistered(name));
    } finally {
      if (ManagementFactory.getPlatformMBeanServer().isRegistered(name)) {
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
      }
    }
  }

  @Test
  void noPropertyProbeHasNoRegistration() {
    assertNull(BTraceMBean.registerMBean(NoPropertyProbe.class));
  }
}
