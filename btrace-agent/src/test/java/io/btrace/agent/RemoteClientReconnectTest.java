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
import io.btrace.core.comm.ExitCommand;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RemoteClientReconnectTest {
  private static final int EXIT_CODE = 41;

  @Test
  void reconnectedClientCommandsReachTheProbeAfterThePeerWentAway() throws Exception {
    CountDownLatch exitRequested = new CountDownLatch(1);
    AtomicInteger requestedExitCode = new AtomicInteger(Integer.MIN_VALUE);
    BTraceRuntime.Impl runtime =
        runtimeProxy(
            exitCode -> {
              requestedExitCode.set(exitCode);
              exitRequested.countDown();
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<RemoteClient> accepted =
          executor.submit(
              () -> {
                Socket socket = server.accept();
                ClientContext context =
                    new ClientContext(null, null, new ArgsMap(), new SharedSettings());
                return RemoteClient.createForTerminalTest(
                    context, protocol(socket), socket, runtime, exitCode -> {});
              });
      RemoteClient remote;
      try (Socket first = new Socket(server.getInetAddress(), server.getLocalPort())) {
        remote = accepted.get(5, TimeUnit.SECONDS);
      }
      // The client side closed its socket, as a detaching client does.
      awaitTrue(remote::isDisconnected, "agent did not notice the client going away");

      Future<Socket> reconnecting = executor.submit(server::accept);
      try (Socket second = new Socket(server.getInetAddress(), server.getLocalPort())) {
        Socket agentSide = reconnecting.get(5, TimeUnit.SECONDS);
        remote.reconnect(protocol(agentSide), agentSide);
        assertFalse(remote.isDisconnected());

        WireProtocol client = protocol(second);
        client.write(new ExitCommand(EXIT_CODE));
        client.flush();

        assertTrue(
            exitRequested.await(5, TimeUnit.SECONDS),
            "EXIT from the reconnected client never reached the probe");
        assertEquals(EXIT_CODE, requestedExitCode.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static WireProtocol protocol(Socket socket) throws java.io.IOException {
    return new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
  }

  private static void awaitTrue(java.util.function.BooleanSupplier condition, String message)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(message);
      }
      Thread.sleep(20);
    }
  }

  private static BTraceRuntime.Impl runtimeProxy(final ExitRecorder recorder) {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            RemoteClientReconnectTest.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              if ("handleExit".equals(method.getName())) {
                recorder.record(((Integer) args[0]).intValue());
              }
              Class<?> type = method.getReturnType();
              if (type == Boolean.TYPE) return Boolean.FALSE;
              if (type == Integer.TYPE) return Integer.valueOf(0);
              if (type == Long.TYPE) return Long.valueOf(0L);
              return null;
            });
  }

  private interface ExitRecorder {
    void record(int exitCode);
  }
}
