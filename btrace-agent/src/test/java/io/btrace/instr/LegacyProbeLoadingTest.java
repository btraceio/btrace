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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.SharedSettings;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Probes compiled with the genuine BTrace 2.2.6 compiler (package {@code org.openjdk.btrace}) must
 * load and pass verification on the current agent, in both the packed and the plain class form.
 */
class LegacyProbeLoadingTest {
  private final BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);

  private static byte[] fixture(String name) throws Exception {
    try (InputStream is = LegacyProbeLoadingTest.class.getResourceAsStream("/legacy/" + name)) {
      assertNotNull(is, "missing fixture " + name);
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = is.read(buf)) > 0) {
        bos.write(buf, 0, n);
      }
      return bos.toByteArray();
    }
  }

  private void assertLegacyProbe(BTraceProbe probe) {
    assertNotNull(probe);
    assertTrue(probe.isVerified(), "legacy probe must pass verification");
    assertTrue(probe.getClassName().contains("SlowChargeProbe"), probe.getClassName());
    assertTrue(!probe.getClassName().startsWith("org.openjdk.btrace"), probe.getClassName());
    List<OnMethod> onMethods = new ArrayList<>();
    probe.onmethods().forEach(onMethods::add);
    assertEquals(1, onMethods.size());
    assertEquals("OrderService", onMethods.get(0).getClazz());
    assertEquals("chargeCard", onMethods.get(0).getMethod());
  }

  @Test
  void packedProbeFrom226LoadsAndVerifies() throws Exception {
    assertLegacyProbe(factory.createProbe(fixture("SlowChargeProbe-2.2.6-pack.class")));
  }

  @Test
  void plainProbeFrom226LoadsAndVerifies() throws Exception {
    assertLegacyProbe(factory.createProbe(fixture("SlowChargeProbe-2.2.6-nopack.class")));
  }
}
