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

import static io.btrace.core.Args.AUTH_TOKEN_FILE;
import static io.btrace.core.Args.BIND_ADDRESS;
import static io.btrace.core.Args.PORT;

import io.btrace.core.ArgsMap;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

final class ControlServer implements Closeable {
  static final String PORT_PROPERTY = "btrace.port";
  static final String ADDRESS_PROPERTY = "btrace.address";
  static final String AUTH_REQUIRED_PROPERTY = "btrace.auth.required";
  static final String TOKEN_FILE_PROPERTY = "btrace.auth.tokenFile";

  private final ServerSocket socket;
  private final PreparedModeCredentials credentials;

  private ControlServer(ServerSocket socket, PreparedModeCredentials credentials) {
    this.socket = socket;
    this.credentials = credentials;
  }

  static ControlServer open(ArgsMap args, boolean preparedMode) throws IOException {
    int port = parsePort(args.get(PORT));
    InetAddress address = resolveAddress(args.get(BIND_ADDRESS));
    PreparedModeCredentials credentials = null;
    ServerSocket socket = null;
    try {
      if (preparedMode) {
        credentials = PreparedModeCredentials.create(args.get(AUTH_TOKEN_FILE));
      }

      socket = new ServerSocket();
      socket.bind(new InetSocketAddress(address, port));
      if (socket.getLocalPort() <= 0) {
        throw new IOException("BTrace control server did not obtain a valid port");
      }

      ControlServer server = new ControlServer(socket, credentials);
      server.publishDiscovery();
      return server;
    } catch (IOException | RuntimeException failure) {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ignored) {
        }
      }
      if (credentials != null) {
        credentials.close();
      }
      throw failure;
    }
  }

  Socket accept() throws IOException {
    return socket.accept();
  }

  boolean isClosed() {
    return socket.isClosed();
  }

  int getPort() {
    return socket.getLocalPort();
  }

  InetAddress getAddress() {
    return socket.getInetAddress();
  }

  byte[] copyAuthenticationToken() {
    return credentials != null ? credentials.copyToken() : null;
  }

  boolean isAuthenticationRequired() {
    return credentials != null;
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    try {
      socket.close();
    } catch (IOException e) {
      failure = e;
    }
    try {
      if (credentials != null) {
        credentials.close();
      }
    } catch (IOException e) {
      if (failure == null) {
        failure = e;
      } else {
        failure.addSuppressed(e);
      }
    } finally {
      clearDiscovery();
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void publishDiscovery() {
    System.setProperty(PORT_PROPERTY, String.valueOf(getPort()));
    System.setProperty(ADDRESS_PROPERTY, getAddress().getHostAddress());
    System.setProperty(AUTH_REQUIRED_PROPERTY, String.valueOf(credentials != null));
    if (credentials != null) {
      System.setProperty(TOKEN_FILE_PROPERTY, credentials.getPath().toString());
    } else {
      System.clearProperty(TOKEN_FILE_PROPERTY);
    }
  }

  private void clearDiscovery() {
    clearIfEqual(PORT_PROPERTY, String.valueOf(getPort()));
    clearIfEqual(ADDRESS_PROPERTY, getAddress().getHostAddress());
    clearIfEqual(AUTH_REQUIRED_PROPERTY, String.valueOf(credentials != null));
    if (credentials != null) {
      clearIfEqual(TOKEN_FILE_PROPERTY, credentials.getPath().toString());
    }
  }

  private static void clearIfEqual(String property, String expected) {
    if (expected.equals(System.getProperty(property))) {
      System.clearProperty(property);
    }
  }

  private static int parsePort(String configuredPort) throws IOException {
    if (configuredPort == null || configuredPort.trim().isEmpty()) {
      return Main.BTRACE_DEFAULT_PORT;
    }
    try {
      int port = Integer.parseInt(configuredPort.trim());
      if (port < 0 || port > 65535) {
        throw new IOException("BTrace control server port is out of range");
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IOException("BTrace control server port is invalid");
    }
  }

  private static InetAddress resolveAddress(String configuredAddress) throws IOException {
    InetAddress address =
        configuredAddress == null || configuredAddress.trim().isEmpty()
            ? InetAddress.getLoopbackAddress()
            : InetAddress.getByName(configuredAddress.trim());
    if (!address.isLoopbackAddress()) {
      throw new IOException("BTrace 3.0 control server supports loopback binding only");
    }
    return address;
  }
}
