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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.HeartbeatCommand;
import io.btrace.core.comm.HeartbeatMonitor;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.ProtocolNegotiator;
import io.btrace.core.comm.ProtocolVersion;
import io.btrace.core.comm.StatusCommand;
import io.btrace.core.comm.WireProtocol;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A probe session notices an agent that stops answering, without waiting for a socket reset. */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ClientHeartbeatTest {
  private static final long INTERVAL_MILLIS = 100;
  private static final long TIMEOUT_MILLIS = 600;

  private interface AgentScript {
    /** Runs after the probe was accepted; returns the number of heartbeats received. */
    int run(WireProtocol protocol, Socket socket) throws Exception;
  }

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadExecutor();
    System.setProperty(HeartbeatMonitor.INTERVAL_PROPERTY, String.valueOf(INTERVAL_MILLIS));
    System.setProperty(HeartbeatMonitor.TIMEOUT_PROPERTY, String.valueOf(TIMEOUT_MILLIS));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    System.clearProperty(HeartbeatMonitor.INTERVAL_PROPERTY);
    System.clearProperty(HeartbeatMonitor.TIMEOUT_PROPERTY);
    System.clearProperty("btrace.comm.protocol");
    System.clearProperty("btrace.comm.autoNegotiate");
  }

  @Test
  void silentAgentEndsTheSessionAsLost() throws Exception {
    List<Command> seen = new CopyOnWriteArrayList<>();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<Integer> agent =
          startAgent(
              server,
              true,
              (protocol, socket) -> {
                // Keep the connection open but never answer: a stopped or wedged agent.
                int heartbeats = 0;
                try {
                  while (true) {
                    if (protocol.read().getType() == Command.HEARTBEAT) {
                      heartbeats++;
                    }
                  }
                } catch (IOException closedByClient) {
                  return heartbeats;
                }
              });
      Client client = client(server);

      long start = System.nanoTime();
      IOException failure =
          assertThrows(
              IOException.class,
              () -> client.submit("localhost", "probe.class", code(), new String[0], seen::add));
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertTrue(
          failure.getMessage().contains("No reply from the BTrace agent"), failure.toString());
      assertTrue(elapsedMillis < 10_000, "took " + elapsedMillis + " ms");
      Command last = seen.get(seen.size() - 1);
      assertEquals(Command.EXIT, last.getType());
      assertEquals(-1, ((ExitCommand) last).getExitCode());
      assertTrue(agent.get(10, TimeUnit.SECONDS) > 0, "the client never sent a heartbeat");
    }
  }

  @Test
  void answeringAgentKeepsAQuietSessionOpenAndRepliesStayInternal() throws Exception {
    List<Command> seen = new CopyOnWriteArrayList<>();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<Integer> agent =
          startAgent(
              server,
              true,
              (protocol, socket) -> {
                // Send no probe output for several timeouts, answering every heartbeat.
                int heartbeats = 0;
                long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4 * TIMEOUT_MILLIS);
                socket.setSoTimeout((int) INTERVAL_MILLIS);
                while (System.nanoTime() < end) {
                  try {
                    Command cmd = protocol.read();
                    if (cmd.getType() == Command.HEARTBEAT) {
                      heartbeats++;
                      protocol.write(new HeartbeatCommand(((HeartbeatCommand) cmd).getNonce()));
                    }
                  } catch (java.net.SocketTimeoutException ignored) {
                    // nothing arrived in this slice
                  }
                }
                protocol.write(new ExitCommand(0));
                protocol.flush();
                return heartbeats;
              });
      Client client = client(server);

      client.submit("localhost", "probe.class", code(), new String[0], seen::add);

      assertTrue(agent.get(10, TimeUnit.SECONDS) >= 3, "too few heartbeats");
      assertFalse(
          seen.stream().anyMatch(c -> c.getType() == Command.HEARTBEAT),
          "a heartbeat reached the listener: " + seen);
      Command last = seen.get(seen.size() - 1);
      assertEquals(0, ((ExitCommand) last).getExitCode());
    }
  }

  @Test
  void v1SessionNeverSendsHeartbeats() throws Exception {
    System.setProperty("btrace.comm.protocol", "1");
    System.setProperty("btrace.comm.autoNegotiate", "false");
    List<Command> seen = new CopyOnWriteArrayList<>();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      AtomicInteger received = new AtomicInteger();
      Future<Integer> agent =
          startAgent(
              server,
              false,
              (protocol, socket) -> {
                // A v1 reader rejects an unknown command type, so any heartbeat fails this read.
                socket.setSoTimeout((int) (3 * TIMEOUT_MILLIS));
                try {
                  protocol.read();
                  received.incrementAndGet();
                } catch (java.net.SocketTimeoutException expected) {
                  // nothing was sent, as it should be
                }
                protocol.write(new ExitCommand(0));
                protocol.flush();
                return received.get();
              });
      Client client = client(server);

      client.submit("localhost", "probe.class", code(), new String[0], seen::add);

      assertEquals(0, (int) agent.get(10, TimeUnit.SECONDS));
      assertEquals(0, ((ExitCommand) seen.get(seen.size() - 1)).getExitCode());
    }
  }

  @Test
  void noHeartbeatIsSentBeforeTheAgentAcceptsTheProbe() throws Exception {
    // An agent that refuses a probe closes the connection; a heartbeat already sent would be
    // unread input there, and on Linux that turns the close into a reset that loses the refusal.
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<Integer> agent =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  PushbackInputStream input =
                      ProtocolNegotiator.createNegotiationStream(socket.getInputStream());
                  new ProtocolNegotiator(ProtocolVersion.V2)
                      .negotiateAgent(input, socket.getOutputStream());
                  WireProtocol protocol = new BinaryWireProtocol(input, socket.getOutputStream());
                  protocol.read(); // SET_PARAMS
                  protocol.read(); // INSTRUMENT
                  int early = 0;
                  socket.setSoTimeout((int) (3 * INTERVAL_MILLIS));
                  try {
                    protocol.read();
                    early++;
                  } catch (java.net.SocketTimeoutException expected) {
                    // nothing was sent while the probe was being deployed
                  }
                  protocol.write(new StatusCommand(-1 * StatusCommand.STATUS_FLAG));
                  protocol.flush();
                  return early;
                }
              });
      Client client = client(server);

      IOException failure =
          assertThrows(
              IOException.class,
              () -> client.submit("localhost", "probe.class", code(), new String[0], c -> {}));

      assertTrue(failure.getMessage().contains("Failed to start"), failure.toString());
      assertEquals(0, (int) agent.get(10, TimeUnit.SECONDS), "heartbeat sent before deploy");
    }
  }

  /** Accepts one client, performs negotiation, consumes settings and instrument, accepts it. */
  private Future<Integer> startAgent(ServerSocket server, boolean v2, AgentScript script) {
    return executor.submit(
        () -> {
          try (Socket socket = server.accept()) {
            WireProtocol protocol;
            if (v2) {
              PushbackInputStream input =
                  ProtocolNegotiator.createNegotiationStream(socket.getInputStream());
              ProtocolVersion negotiated =
                  new ProtocolNegotiator(ProtocolVersion.V2)
                      .negotiateAgent(input, socket.getOutputStream());
              assertEquals(ProtocolVersion.V2, negotiated);
              protocol = new BinaryWireProtocol(input, socket.getOutputStream());
            } else {
              protocol =
                  new JavaSerializationProtocol(socket.getInputStream(), socket.getOutputStream());
            }
            protocol.read(); // SET_PARAMS
            protocol.read(); // INSTRUMENT
            protocol.write(new StatusCommand(StatusCommand.STATUS_FLAG));
            protocol.flush();
            return script.run(protocol, socket);
          }
        });
  }

  private static Client client(ServerSocket server) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("btrace.port", String.valueOf(server.getLocalPort()));
    properties.setProperty("btrace.address", "127.0.0.1");
    Client client = new Client(0);
    client.configureConnection(properties);
    return client;
  }

  private static byte[] code() throws Exception {
    return Files.readAllBytes(
        Paths.get(ClientHeartbeatTest.class.getResource("ClientHeartbeatTest.class").toURI()));
  }
}
