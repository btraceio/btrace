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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.HeartbeatCommand;
import io.btrace.core.comm.HeartbeatMonitor;
import io.btrace.core.comm.WireProtocol;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The agent detaches a probe whose client stopped answering, and keeps the probe running. */
class RemoteClientHeartbeatTest {
  private static final long TIMEOUT_MILLIS = 500;

  private ExecutorService executor;
  private final CountDownLatch exitRequested = new CountDownLatch(1);

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadExecutor();
    System.setProperty(HeartbeatMonitor.TIMEOUT_PROPERTY, String.valueOf(TIMEOUT_MILLIS));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    System.clearProperty(HeartbeatMonitor.TIMEOUT_PROPERTY);
  }

  @Test
  void clientThatGoesSilentAfterAHeartbeatIsDetachedAndCanReconnect() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<RemoteClient> accepted = accept(server);
      try (Socket first = new Socket(server.getInetAddress(), server.getLocalPort())) {
        RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
        WireProtocol client = protocol(first);
        client.write(new HeartbeatCommand(7));
        client.flush();
        Command reply = client.read();
        assertEquals(Command.HEARTBEAT, reply.getType());
        assertEquals(7, ((HeartbeatCommand) reply).getNonce());

        // The socket stays open but the client sends nothing more: no reset ever arrives.
        awaitTrue(remote::isDisconnected, "the silent client was never detached");
        assertEquals(1, exitRequested.getCount(), "detaching must not stop the probe");

        Future<Socket> reconnecting = executor.submit(server::accept);
        try (Socket second = new Socket(server.getInetAddress(), server.getLocalPort())) {
          Socket agentSide = reconnecting.get(5, TimeUnit.SECONDS);
          remote.reconnect(protocol(agentSide), agentSide);
          assertFalse(remote.isDisconnected());

          WireProtocol resumed = protocol(second);
          resumed.write(new ExitCommand(3));
          resumed.flush();
          assertTrue(
              exitRequested.await(5, TimeUnit.SECONDS),
              "EXIT from the reconnected client never reached the probe");
        }
      }
    }
  }

  @Test
  void clientThatNeverSendsHeartbeatsIsNotTimedOut() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<RemoteClient> accepted = accept(server);
      try (Socket first = new Socket(server.getInetAddress(), server.getLocalPort())) {
        RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
        Thread.sleep(3 * TIMEOUT_MILLIS);
        assertFalse(remote.isDisconnected(), "a client without heartbeats was timed out");
      }
    }
  }

  private Future<RemoteClient> accept(ServerSocket server) {
    BTraceRuntime.Impl runtime = runtimeProxy();
    return executor.submit(
        () -> {
          Socket socket = server.accept();
          ClientContext context =
              new ClientContext(null, null, new ArgsMap(), new SharedSettings());
          return RemoteClient.createForTerminalTest(
              context, protocol(socket), socket, runtime, exitCode -> {});
        });
  }

  private static WireProtocol protocol(Socket socket) throws java.io.IOException {
    return new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
  }

  private static void awaitTrue(java.util.function.BooleanSupplier condition, String message)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(message);
      }
      Thread.sleep(20);
    }
  }

  private BTraceRuntime.Impl runtimeProxy() {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            RemoteClientHeartbeatTest.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              if ("handleExit".equals(method.getName())) {
                exitRequested.countDown();
              }
              Class<?> type = method.getReturnType();
              if (type == Boolean.TYPE) return Boolean.FALSE;
              if (type == Integer.TYPE) return Integer.valueOf(0);
              if (type == Long.TYPE) return Long.valueOf(0L);
              return null;
            });
  }
}
