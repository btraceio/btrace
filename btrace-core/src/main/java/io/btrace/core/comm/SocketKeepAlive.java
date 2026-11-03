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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns on TCP keepalive for a client-agent connection, so the operating system eventually notices
 * a peer host that went away. This backs up heartbeats on connections that have none, such as v1
 * sessions with a 2.x peer.
 *
 * <p>With the operating system's defaults the first probe is only sent after about two hours of
 * idleness. Where the JDK supports it (11 and later on Linux and macOS, through {@code
 * jdk.net.ExtendedSocketOptions}), the probes are tuned to start after {@value #IDLE_SECONDS}
 * seconds; the options are looked up reflectively because BTrace also runs on JDK 8.
 *
 * <p>Keepalive cannot detect a peer whose host still answers TCP while the process is hung;
 * heartbeats cover that.
 */
public final class SocketKeepAlive {
  static final int IDLE_SECONDS = 30;
  static final int INTERVAL_SECONDS = 10;
  static final int COUNT = 3;

  private static final Logger log = LoggerFactory.getLogger(SocketKeepAlive.class);

  private SocketKeepAlive() {}

  /** Enables keepalive on {@code socket}, best effort; failures are logged and ignored. */
  public static void enable(Socket socket) {
    try {
      socket.setKeepAlive(true);
    } catch (SocketException e) {
      log.debug("Unable to enable TCP keepalive: {}", e.toString());
      return;
    }
    setExtendedOption(socket, "TCP_KEEPIDLE", IDLE_SECONDS);
    setExtendedOption(socket, "TCP_KEEPINTERVAL", INTERVAL_SECONDS);
    setExtendedOption(socket, "TCP_KEEPCOUNT", COUNT);
  }

  private static void setExtendedOption(Socket socket, String name, int value) {
    try {
      Class<?> options = Class.forName("jdk.net.ExtendedSocketOptions");
      Field field = options.getField(name);
      Object option = field.get(null);
      Class<?> socketOption = Class.forName("java.net.SocketOption");
      Method supported = Socket.class.getMethod("supportedOptions");
      if (!((java.util.Set<?>) supported.invoke(socket)).contains(option)) {
        return;
      }
      Method setOption = Socket.class.getMethod("setOption", socketOption, Object.class);
      setOption.invoke(socket, option, value);
    } catch (ReflectiveOperationException | RuntimeException e) {
      // JDK 8, a platform without the option, or a module that does not export jdk.net.
      if (log.isDebugEnabled()) {
        log.debug("TCP keepalive option {} not applied: {}", name, e.toString());
      }
    }
  }
}
