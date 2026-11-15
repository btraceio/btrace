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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.InstrumentCommand;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.ProtocolVersion;
import io.btrace.core.comm.WireProtocol;
import io.btrace.instr.BTraceProbe;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

  @Test
  void terminalExitFinalizesWhenTheExitEchoCannotBeWritten() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      CountDownLatch finalizerCalled = new CountDownLatch(1);
      AtomicInteger finalizerExitCode = new AtomicInteger(Integer.MIN_VALUE);
      BTraceRuntime.Impl runtime = runtimeProxy(exitCode -> {});
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<RemoteClient> accepted =
            executor.submit(
                () -> {
                  Socket socket = server.accept();
                  // A client that has already closed its socket: echoing the Exit fails.
                  WireProtocol protocol =
                      new ExitWriteFailingProtocol(
                          new BinaryWireProtocol(
                              socket.getInputStream(), socket.getOutputStream()));
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
          RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);

          remote.onCommand(new ExitCommand(FIRST_EXIT_CODE));

          assertTrue(finalizerCalled.await(5, TimeUnit.SECONDS));
          assertEquals(FIRST_EXIT_CODE, finalizerExitCode.get());
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void budgetExitRemovesDetachedProbe() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      AtomicInteger requestedExitCode = new AtomicInteger(Integer.MIN_VALUE);
      BTraceRuntime.Impl runtime = runtimeProxy(requestedExitCode::set);
      AtomicInteger unregistered = new AtomicInteger();
      String probeName = "test.budget.DetachedProbe";
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<RemoteClient> accepted = executor.submit(() -> acceptClient(server, runtime, null));
        RemoteClient remote;
        try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
          remote = accepted.get(5, TimeUnit.SECONDS);
          remote.setProbeForTest(probeProxy(probeName, unregistered), System.nanoTime());
        }
        // The client side closed its socket: the probe is detached and keeps running.
        awaitTrue(remote::isDisconnected, "agent did not notice the client going away");
        String listed = remote.id + " [" + probeName + "]";
        assertTrue(Client.listProbes().contains(listed));

        remote.budgetExit("probe " + probeName + " removed: lifetime exceeded probeTtl=1s");
        // The exit cannot reach a client that is gone: the budget governor sees the lost transport
        // and finishes the removal.
        assertTrue(remote.transportLost(), "the lost transport was not reported");
        remote.forceBudgetRemoval();

        assertEquals(Client.BUDGET_EXIT_CODE, requestedExitCode.get());
        assertFalse(Client.listProbes().contains(listed), "detached probe is still listed");
        assertEquals(1, unregistered.get(), "the probe's transformer was not cleaned up");
        assertTrue(remote.isShuttingDown());
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void budgetExitAfterClientExitIsHarmless() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      BTraceRuntime.Impl runtime = runtimeProxy(exitCode -> {});
      AtomicInteger unregistered = new AtomicInteger();
      String probeName = "test.budget.ExitedProbe";
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        // No test finalizer: the normal exit runs the real Client.onExit.
        Future<RemoteClient> accepted = executor.submit(() -> acceptClient(server, runtime, null));
        try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
          RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
          remote.setProbeForTest(probeProxy(probeName, unregistered), System.nanoTime());

          remote.onCommand(new ExitCommand(FIRST_EXIT_CODE));
          assertTrue(remote.isShuttingDown());
          assertEquals(1, unregistered.get());

          assertDoesNotThrow(
              () -> remote.budgetExit("probe " + probeName + " removed: late budget exit"));

          assertEquals(1, unregistered.get(), "finalization ran more than once");
          assertFalse(Client.listProbes().contains(remote.id + " [" + probeName + "]"));
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void budgetExitRemovesProbeDetachedDuringExit() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      AtomicReference<Socket> peer = new AtomicReference<>();
      AtomicReference<RemoteClient> client = new AtomicReference<>();
      AtomicInteger requestedExitCode = new AtomicInteger(Integer.MIN_VALUE);
      // The client goes away while the runtime is being torn down: its transport is released
      // before the runtime's ExitCommand could be dispatched, so that command is dropped.
      BTraceRuntime.Impl runtime =
          runtimeProxy(
              exitCode -> {
                requestedExitCode.set(exitCode);
                try {
                  peer.get().close();
                  awaitTrue(client.get()::isDisconnected, "transport was not released");
                } catch (IOException | InterruptedException e) {
                  throw new IllegalStateException(e);
                }
              });
      AtomicInteger unregistered = new AtomicInteger();
      String probeName = "test.budget.DetachingProbe";
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<RemoteClient> accepted = executor.submit(() -> acceptClient(server, runtime, null));
        Socket socket = new Socket(server.getInetAddress(), server.getLocalPort());
        peer.set(socket);
        RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
        client.set(remote);
        remote.setProbeForTest(probeProxy(probeName, unregistered), System.nanoTime());
        assertFalse(remote.isDisconnected());

        remote.budgetExit("probe " + probeName + " removed: lifetime exceeded probeTtl=1s");
        // The exit cannot reach a client that is gone: the budget governor sees the lost transport
        // and finishes the removal.
        assertTrue(remote.transportLost(), "the lost transport was not reported");
        remote.forceBudgetRemoval();

        assertEquals(Client.BUDGET_EXIT_CODE, requestedExitCode.get());
        assertFalse(
            Client.listProbes().contains(remote.id + " [" + probeName + "]"),
            "probe detached during the exit is still listed");
        assertEquals(1, unregistered.get(), "the probe's transformer was not cleaned up");
        assertTrue(remote.isShuttingDown());
      } finally {
        Socket socket = peer.get();
        if (socket != null) {
          socket.close();
        }
        executor.shutdownNow();
      }
    }
  }

  @Test
  void budgetExitOnConnectedClientSendsReasonThenExits() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      AtomicReference<RemoteClient> client = new AtomicReference<>();
      List<String> events = Collections.synchronizedList(new ArrayList<>());
      // Delivers sent commands to the client as the runtime's command thread does; handleExit
      // only records the request, so whatever finishes the exit has to come from budgetExit.
      BTraceRuntime.Impl runtime =
          runtimeProxy(
              exitCode -> events.add("exit " + exitCode),
              command -> {
                events.add("send " + command.getClass().getSimpleName());
                try {
                  client.get().onCommand((Command) command);
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              });
      String probeName = "test.budget.ConnectedProbe";
      String reason = "probe " + probeName + " removed: 5000 hits/s exceeded probeMaxRate=1000";
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<RemoteClient> accepted = executor.submit(() -> acceptClient(server, runtime, null));
        try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
          WireProtocol protocol =
              new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
          RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
          client.set(remote);
          remote.setProbeForTest(probeProxy(probeName, new AtomicInteger()), System.nanoTime());

          remote.budgetExit(reason);
          // onCommand flushes the transport before dispatching; NULL itself is not sent.
          remote.onCommand(Command.NULL);

          socket.setSoTimeout(5000);
          Command received = protocol.read();
          assertTrue(received instanceof MessageCommand, String.valueOf(received));
          assertEquals("BTrace: " + reason, ((MessageCommand) received).getMessage().trim());
          assertEquals(
              Arrays.asList("send MessageCommand", "exit " + Client.BUDGET_EXIT_CODE), events);
          assertFalse(
              remote.isShuttingDown(), "a connected probe must exit through the runtime handshake");
          assertFalse(remote.isDisconnected());
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void forcedBudgetRemovalReleasesWriterBlockedOnUnreadClient() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      BTraceRuntime.Impl runtime = runtimeProxy(exitCode -> {});
      String probeName = "test.budget.UnreadProbe";
      ExecutorService executor = Executors.newCachedThreadPool();
      // A client that connected and then stopped reading (a paused pipe, Ctrl-Z): the agent's
      // writes fill the socket buffers and block. The V1 protocol is used because closing its
      // ObjectOutputStream flushes into that same full socket.
      Socket peer = new Socket();
      try {
        peer.setReceiveBufferSize(4096);
        Future<RemoteClient> accepted =
            executor.submit(
                () -> {
                  Socket socket = server.accept();
                  socket.setSendBufferSize(4096);
                  WireProtocol protocol =
                      new JavaSerializationProtocol(
                          socket.getInputStream(), socket.getOutputStream());
                  ClientContext context =
                      new ClientContext(null, null, new ArgsMap(), new SharedSettings());
                  return RemoteClient.createForTerminalTest(
                      context, protocol, socket, runtime, null);
                });
        peer.connect(server.getLocalSocketAddress());
        new ObjectOutputStream(peer.getOutputStream()).flush();
        RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
        remote.setProbeForTest(probeProxy(probeName, new AtomicInteger()), System.nanoTime());

        // Short lines, like a probe's println output, go through ObjectOutputStream's block buffer.
        String payload = "budget-output line from a hot probe";
        AtomicLong written = new AtomicLong();
        Future<?> writer =
            executor.submit(
                () -> {
                  while (!remote.isShuttingDown()) {
                    remote.onCommand(new MessageCommand(payload));
                    written.incrementAndGet();
                  }
                  return null;
                });
        long last = -1;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (written.get() != last && System.nanoTime() < deadline) {
          last = written.get();
          Thread.sleep(500);
        }
        assertFalse(writer.isDone(), "the writer never blocked");
        assertEquals(last, written.get(), "the writer never blocked");

        Future<?> removal = executor.submit(remote::forceBudgetRemoval);
        assertDoesNotThrow(
            () -> removal.get(10, TimeUnit.SECONDS),
            "forced removal hung on the client that stopped reading");
        assertTrue(remote.isShuttingDown());
        assertDoesNotThrow(
            () -> writer.get(5, TimeUnit.SECONDS), "the blocked writer was not released");
        assertFalse(Client.listProbes().contains(remote.id + " [" + probeName + "]"));
      } finally {
        peer.close();
        executor.shutdownNow();
      }
    }
  }

  @Test
  void trustedDeployIsRefusedUnderBudget() throws Exception {
    List<Command> received = deployOverSocket(true, true);
    assertTrue(received.size() >= 2, String.valueOf(received));
    assertTrue(received.get(0) instanceof MessageCommand, String.valueOf(received));
    assertEquals(
        "BTrace: " + BudgetLimits.TRUSTED_REFUSED,
        ((MessageCommand) received.get(0)).getMessage().trim());
    assertTrue(received.get(1) instanceof ExitCommand, String.valueOf(received));
    assertEquals(1, ((ExitCommand) received.get(1)).getExitCode());
  }

  @Test
  void verifiedDeployIsNotRefusedUnderBudget() throws Exception {
    assertNoRefusal(deployOverSocket(false, true));
  }

  @Test
  void trustedDeployIsNotRefusedWithoutBudget() throws Exception {
    assertNoRefusal(deployOverSocket(true, false));
  }

  private static void assertNoRefusal(List<Command> received) {
    for (Command command : received) {
      if (command instanceof MessageCommand) {
        assertFalse(
            ((MessageCommand) command).getMessage().contains(BudgetLimits.TRUSTED_REFUSED),
            String.valueOf(received));
      }
    }
  }

  /**
   * Deploys a probe whose bytes are not a class over a real connection that has no runtime yet, as
   * a fresh deploy has, and returns what the client received until the agent closed it.
   */
  private static List<Command> deployOverSocket(boolean trusted, boolean budget) throws Exception {
    List<Command> received = new ArrayList<>();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Client.refuseTrustedMode(budget);
      try {
        Future<RemoteClient> accepted =
            executor.submit(
                () -> {
                  Socket socket = server.accept();
                  WireProtocol protocol =
                      new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
                  SharedSettings settings = new SharedSettings();
                  settings.setTrusted(trusted);
                  ClientContext context = new ClientContext(null, null, new ArgsMap(), settings);
                  return RemoteClient.createForTerminalTest(context, protocol, socket, null, null);
                });
        try (Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
          RemoteClient remote = accepted.get(5, TimeUnit.SECONDS);
          WireProtocol peer =
              new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
          assertNull(remote.loadClass(new InstrumentCommand(new byte[] {1, 2, 3}, new ArgsMap())));
          assertEquals("<unknown>", remote.getClassName(), "a probe was loaded");
          if (trusted && budget) {
            assertFalse(Client.liveClients().contains(remote), "a refused client stays registered");
          }
          socket.setSoTimeout(2000);
          try {
            while (received.size() < 10) {
              received.add(peer.read());
            }
          } catch (IOException endOfStream) {
            // the agent closed the connection, or sent nothing more
          }
        }
      } finally {
        Client.refuseTrustedMode(false);
        executor.shutdownNow();
      }
    }
    return received;
  }

  private static RemoteClient acceptClient(
      ServerSocket server, BTraceRuntime.Impl runtime, IntConsumer finalizer) throws IOException {
    Socket socket = server.accept();
    WireProtocol protocol =
        new BinaryWireProtocol(socket.getInputStream(), socket.getOutputStream());
    ClientContext context = new ClientContext(null, null, new ArgsMap(), new SharedSettings());
    return RemoteClient.createForTerminalTest(context, protocol, socket, runtime, finalizer);
  }

  private static void awaitTrue(BooleanSupplier condition, String message)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(message);
      }
      Thread.sleep(20);
    }
  }

  /** A probe proxy answering its names and counting {@code unregister()} calls. */
  private static BTraceProbe probeProxy(String className, AtomicInteger unregistered) {
    return (BTraceProbe)
        Proxy.newProxyInstance(
            RemoteClientTerminalExitTest.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getClassName":
                  boolean internal = args != null && (Boolean) args[0];
                  return internal ? className.replace('.', '/') : className;
                case "unregister":
                  unregistered.incrementAndGet();
                  return null;
                default:
                  return method.getReturnType() == Boolean.TYPE ? Boolean.FALSE : null;
              }
            });
  }

  private static final class ExitWriteFailingProtocol implements WireProtocol {
    private final WireProtocol delegate;

    ExitWriteFailingProtocol(WireProtocol delegate) {
      this.delegate = delegate;
    }

    @Override
    public Command read() throws IOException, ClassNotFoundException {
      return delegate.read();
    }

    @Override
    public void write(Command command) throws IOException {
      if (command instanceof ExitCommand) {
        throw new SocketException("Broken pipe");
      }
      delegate.write(command);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public ProtocolVersion getVersion() {
      return delegate.getVersion();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  private static BTraceRuntime.Impl runtimeProxy(final ExitRecorder recorder) {
    return runtimeProxy(recorder, command -> {});
  }

  private static BTraceRuntime.Impl runtimeProxy(
      final ExitRecorder recorder, final Consumer<Object> sender) {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            RemoteClientTerminalExitTest.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              if ("handleExit".equals(method.getName())) {
                recorder.record(((Integer) args[0]).intValue());
              } else if ("sendCommand".equals(method.getName())) {
                sender.accept(args[0]);
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
