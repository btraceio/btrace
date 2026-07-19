/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.statsd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.btrace.core.SharedSettings;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StatsdImplTest {
  private final String originalHost = SharedSettings.GLOBAL.getStatsdHost();
  private final int originalPort = SharedSettings.GLOBAL.getStatsdPort();

  @AfterEach
  void restoreSettings() {
    SharedSettings.GLOBAL.setStatsdHost(originalHost);
    SharedSettings.GLOBAL.setStatsdPort(originalPort);
  }

  @Test
  void sendsRepeatedIncrementsThroughInitializedSocket() throws Exception {
    try (DatagramSocket receiver = new DatagramSocket(0)) {
      receiver.setSoTimeout(1000);
      SharedSettings.GLOBAL.setStatsdHost("127.0.0.1");
      SharedSettings.GLOBAL.setStatsdPort(receiver.getLocalPort());
      StatsdImpl statsd = new StatsdImpl();
      statsd.initialize(null);

      statsd.increment("requests", "region:west");
      statsd.increment("requests", "region:west");

      assertEquals("requests:1|c|#region:west", receive(receiver));
      assertEquals("requests:1|c|#region:west", receive(receiver));
      statsd.close();
      assertDoesNotThrow(() -> statsd.increment("requests"));
      assertThrows(SocketTimeoutException.class, () -> receive(receiver));
    }
  }

  @Test
  void disablesEmissionWhenInitializationFails() {
    SharedSettings.GLOBAL.setStatsdHost("invalid.invalid");
    StatsdImpl statsd = new StatsdImpl();

    assertDoesNotThrow(() -> statsd.initialize(null));
    assertDoesNotThrow(() -> statsd.increment("requests"));
    assertDoesNotThrow(statsd::close);
  }

  private static String receive(DatagramSocket receiver) throws Exception {
    byte[] bytes = new byte[1024];
    DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
    receiver.receive(packet);
    return new String(
        packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.US_ASCII);
  }
}
