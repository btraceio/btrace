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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SocketKeepAliveTest {

  @Test
  void enablesKeepAliveAndTunesItWhereSupported() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
      SocketKeepAlive.enable(socket);

      assertTrue(socket.getKeepAlive());
      assertOption(socket, "TCP_KEEPIDLE", SocketKeepAlive.IDLE_SECONDS);
      assertOption(socket, "TCP_KEEPINTERVAL", SocketKeepAlive.INTERVAL_SECONDS);
      assertOption(socket, "TCP_KEEPCOUNT", SocketKeepAlive.COUNT);
    }
  }

  /**
   * Checks a jdk.net option when this JDK and platform have it. Reflective, because the tests also
   * compile and run on JDK 8, which has neither the options nor {@code Socket.getOption}.
   */
  private static void assertOption(Socket socket, String name, int expected) throws Exception {
    Object option;
    try {
      option = Class.forName("jdk.net.ExtendedSocketOptions").getField(name).get(null);
    } catch (ReflectiveOperationException e) {
      return;
    }
    Set<?> supported = (Set<?>) Socket.class.getMethod("supportedOptions").invoke(socket);
    if (!supported.contains(option)) {
      return;
    }
    Method getOption = Socket.class.getMethod("getOption", Class.forName("java.net.SocketOption"));
    assertEquals(expected, getOption.invoke(socket, option));
  }
}
