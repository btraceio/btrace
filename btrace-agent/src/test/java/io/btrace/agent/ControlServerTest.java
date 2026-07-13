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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ControlServerTest {
  @AfterEach
  void clearDiscoveryProperties() {
    System.clearProperty(ControlServer.PORT_PROPERTY);
    System.clearProperty(ControlServer.ADDRESS_PROPERTY);
    System.clearProperty(ControlServer.AUTH_REQUIRED_PROPERTY);
    System.clearProperty(ControlServer.TOKEN_FILE_PROPERTY);
  }

  @Test
  void preparedModePublishesEphemeralLoopbackEndpointAndRemovesCredentials() throws Exception {
    ControlServer server = ControlServer.open(new ArgsMap(new String[] {"port=0"}), true);
    Path tokenFile = Paths.get(System.getProperty(ControlServer.TOKEN_FILE_PROPERTY));
    try {
      assertTrue(server.getAddress().isLoopbackAddress());
      assertTrue(server.getPort() > 0);
      assertEquals(
          String.valueOf(server.getPort()), System.getProperty(ControlServer.PORT_PROPERTY));
      assertEquals("true", System.getProperty(ControlServer.AUTH_REQUIRED_PROPERTY));
      assertTrue(Files.isRegularFile(tokenFile));
      assertTrue(server.copyAuthenticationToken().length > 0);
    } finally {
      server.close();
    }

    assertTrue(server.isClosed());
    assertFalse(Files.exists(tokenFile));
    assertNull(System.getProperty(ControlServer.PORT_PROPERTY));
    assertNull(System.getProperty(ControlServer.TOKEN_FILE_PROPERTY));
  }

  @Test
  void dynamicModeUsesLoopbackWithoutAuthentication() throws Exception {
    ControlServer server = ControlServer.open(new ArgsMap(new String[] {"port=0"}), false);
    try {
      assertTrue(server.getAddress().isLoopbackAddress());
      assertEquals("false", System.getProperty(ControlServer.AUTH_REQUIRED_PROPERTY));
      assertNull(System.getProperty(ControlServer.TOKEN_FILE_PROPERTY));
      assertNull(server.copyAuthenticationToken());
    } finally {
      server.close();
    }
  }

  @Test
  void wildcardBindingFailsClosed() {
    ArgsMap args = new ArgsMap(new String[] {"port=0", "bindAddress=0.0.0.0"});

    assertThrows(IOException.class, () -> ControlServer.open(args, true));
    assertNull(System.getProperty(ControlServer.PORT_PROPERTY));
  }

  @Test
  void explicitIpv4LoopbackIsSupported() throws Exception {
    ArgsMap args = new ArgsMap(new String[] {"port=0", "bindAddress=127.0.0.1"});

    try (ControlServer server = ControlServer.open(args, true)) {
      assertEquals(InetAddress.getByName("127.0.0.1"), server.getAddress());
    }
  }

  @Test
  void explicitIpv6LoopbackIsSupportedWhenAvailable() throws Exception {
    InetAddress ipv6 = InetAddress.getByName("::1");
    Assumptions.assumeTrue(NetworkInterface.getByInetAddress(ipv6) != null);
    ArgsMap args = new ArgsMap(new String[] {"port=0", "bindAddress=::1"});

    try (ControlServer server = ControlServer.open(args, true)) {
      assertTrue(server.getAddress().isLoopbackAddress());
      assertEquals(ipv6, server.getAddress());
    }
  }

  @Test
  void invalidPortFailsClosed() {
    assertThrows(
        IOException.class,
        () -> ControlServer.open(new ArgsMap(new String[] {"port=70000"}), true));
  }
}
