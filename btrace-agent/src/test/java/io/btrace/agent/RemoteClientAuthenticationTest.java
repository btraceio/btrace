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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ConnectionAuthenticator;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.ListProbesCommand;
import io.btrace.core.comm.ProtocolNegotiator;
import io.btrace.core.comm.ProtocolVersion;
import io.btrace.core.comm.WireProtocol;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RemoteClientAuthenticationTest {
  private static final byte[] TOKEN = "prepared-secret".getBytes(StandardCharsets.UTF_8);

  @AfterEach
  void clearTimeout() {
    System.clearProperty("btrace.protocol.negotiation.timeout");
  }

  @Test
  void authenticatedV1CanListProbes() throws Exception {
    runAuthenticatedListSession(ProtocolVersion.V1);
  }

  @Test
  void authenticatedV2CanListProbes() throws Exception {
    runAuthenticatedListSession(ProtocolVersion.V2);
  }

  @Test
  void incorrectCredentialIsRejectedBeforeClientContext() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<?> accepted =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  assertThrows(
                      IOException.class,
                      () -> RemoteClient.getClient(null, socket, TOKEN.clone(), client -> null));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
        assertThrows(
            IOException.class,
            () ->
                ConnectionAuthenticator.authenticateClient(
                    socket.getInputStream(),
                    socket.getOutputStream(),
                    "wrong-secret".getBytes(StandardCharsets.UTF_8)));
      } finally {
        accepted.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();
      }
    }
  }

  @Test
  void incompleteAuthenticationIsBounded() throws Exception {
    System.setProperty("btrace.protocol.negotiation.timeout", "100");
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<?> accepted =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  assertThrows(
                      IOException.class,
                      () -> RemoteClient.getClient(null, socket, TOKEN.clone(), client -> null));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      try (Socket ignored = new Socket(server.getInetAddress(), server.getLocalPort())) {
        accepted.get(5, TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static void runAuthenticatedListSession(ProtocolVersion version) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ClientContext context = new ClientContext(null, null, new ArgsMap(), new SharedSettings());
      Future<Client> accepted =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  return RemoteClient.getClient(context, socket, TOKEN.clone(), client -> null);
                }
              });

      try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
        ConnectionAuthenticator.authenticateClient(
            socket.getInputStream(), socket.getOutputStream(), TOKEN);
        try (WireProtocol protocol = createClientProtocol(version, socket)) {
          protocol.write(new ListProbesCommand());
          protocol.flush();
          Command response = protocol.read();
          assertTrue(response instanceof ListProbesCommand);
          protocol.write(new ExitCommand(0));
          protocol.flush();
        }
      }

      try {
        assertNull(accepted.get(5, TimeUnit.SECONDS));
      } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
          throw (Exception) cause;
        }
        throw e;
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static WireProtocol createClientProtocol(ProtocolVersion version, Socket socket)
      throws Exception {
    if (version == ProtocolVersion.V1) {
      return new JavaSerializationProtocol(socket.getInputStream(), socket.getOutputStream());
    }
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    assertEquals(
        ProtocolVersion.V2,
        negotiator.negotiateClient(
            socket.getInputStream(), socket.getOutputStream(), ProtocolVersion.V2));
    return new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
  }
}
