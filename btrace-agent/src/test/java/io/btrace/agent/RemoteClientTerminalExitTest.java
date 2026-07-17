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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.WireProtocol;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RemoteClientTerminalExitTest {
  private static final int FIRST_EXIT_CODE = 37;

  @Test
  void inboundExitRetainsOutputUntilRuntimeMarkerAndGeneratedExit() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      CountDownLatch runtimeExitRequested = new CountDownLatch(1);
      AtomicInteger requestedExitCode = new AtomicInteger(Integer.MIN_VALUE);
      CountDownLatch finalizerCalled = new CountDownLatch(1);
      AtomicInteger finalizerExitCode = new AtomicInteger(Integer.MIN_VALUE);
      BTraceRuntime.Impl runtime =
          runtimeProxy(
              exitCode -> {
                requestedExitCode.set(exitCode);
                runtimeExitRequested.countDown();
              });
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<RemoteClient> accepted =
            executor.submit(
                () -> {
                  Socket socket = server.accept();
                  WireProtocol protocol =
                      new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
                  ClientContext context =
                      new ClientContext(null, null, new ArgsMap(), new SharedSettings());
                  return RemoteClient.createForTerminalTest(
                      context,
                      protocol,
                      socket,
                      runtime,
                      exitCode -> {
                        finalizerExitCode.set(exitCode);
                        finalizerCalled.countDown();
                      });
                });
        try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
          WireProtocol protocol =
              new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
          RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);

          protocol.write(new ExitCommand(FIRST_EXIT_CODE));
          protocol.flush();
          assertTrue(runtimeExitRequested.await(5, TimeUnit.SECONDS));
          assertEquals(FIRST_EXIT_CODE, requestedExitCode.get());

          // The reader has returned, but it deliberately retains the output protocol while the
          // runtime's terminal marker is pending. A timeout is evidence of no echo/no EOF.
          socket.setSoTimeout(250);
          assertThrows(SocketTimeoutException.class, protocol::read);
          assertFalse(socket.isClosed());
          socket.setSoTimeout(5000);

          MessageCommand marker = new MessageCommand("[BTRACE] terminal cleanup: mbean=closed");
          remote.onCommand(marker);
          remote.onCommand(new ExitCommand(FIRST_EXIT_CODE));

          List<Command> received = new ArrayList<>();
          received.add(protocol.read());
          received.add(protocol.read());
          assertEquals(marker.getMessage(), ((MessageCommand) received.get(0)).getMessage());
          assertTrue(received.get(1) instanceof ExitCommand);
          assertEquals(FIRST_EXIT_CODE, ((ExitCommand) received.get(1)).getExitCode());
          assertTrue(finalizerCalled.await(5, TimeUnit.SECONDS));
          assertEquals(FIRST_EXIT_CODE, finalizerExitCode.get());
          assertThrows(IOException.class, protocol::read);
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static BTraceRuntime.Impl runtimeProxy(final ExitRecorder recorder) {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            RemoteClientTerminalExitTest.class.getClassLoader(),
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
