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

import io.btrace.core.*;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ConnectionAuthenticator;
import io.btrace.core.comm.DisconnectCommand;
import io.btrace.core.comm.EventCommand;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.InstrumentCommand;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.ListFailedExtensionsCommand;
import io.btrace.core.comm.ListProbesCommand;
import io.btrace.core.comm.PrintableCommand;
import io.btrace.core.comm.ProtocolConfig;
import io.btrace.core.comm.ProtocolNegotiator;
import io.btrace.core.comm.ProtocolVersion;
import io.btrace.core.comm.ReconnectCommand;
import io.btrace.core.comm.SetSettingsCommand;
import io.btrace.core.comm.StatusCommand;
import io.btrace.core.comm.WireProtocol;
import io.btrace.extension.ExtensionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a remote client communicated by socket.
 *
 * @author A. Sundararajan
 */
@SuppressWarnings({"SynchronizeOnNonFinalField", "SynchronizationOnLocalVariableOrMethodParameter"})
class RemoteClient extends Client {
  private static final Logger log = LoggerFactory.getLogger(RemoteClient.class);
  private static final long TERMINAL_HANDSHAKE_WAIT_MILLIS = 2000L;

  /**
   * Retains the output side after the inbound reader has consumed an ExitCommand. Only a
   * successfully written runtime-generated Exit completes this handshake and permits final agent
   * cleanup/transport close.
   */
  private static final class TerminalHandshake {
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicBoolean finalizationStarted = new AtomicBoolean(false);

    boolean awaitCompletion() throws InterruptedException {
      return completed.await(TERMINAL_HANDSHAKE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }

    void complete() {
      completed.countDown();
    }
  }

  private final class DelayedCommandExecutor implements Function<Command, Boolean> {
    private final boolean isConnected;

    public DelayedCommandExecutor(boolean isConnected) {
      this.isConnected = isConnected;
    }

    @Override
    public Boolean apply(Command value) {
      return dispatchCommand(value, isConnected);
    }
  }

  private volatile Socket sock;
  private volatile WireProtocol protocol;
  private volatile boolean disconnected = false;
  private final AtomicReferenceFieldUpdater<RemoteClient, Socket> sockUpdater =
      AtomicReferenceFieldUpdater.newUpdater(RemoteClient.class, Socket.class, "sock");
  private final AtomicReferenceFieldUpdater<RemoteClient, WireProtocol> protocolUpdater =
      AtomicReferenceFieldUpdater.newUpdater(RemoteClient.class, WireProtocol.class, "protocol");

  private final CircularBuffer<Command> delayedCommands = new CircularBuffer<>(5000);
  private final AtomicReference<TerminalHandshake> terminalHandshake = new AtomicReference<>();
  private final IntConsumer terminalExitFinalizer;

  static Client getClient(
      ClientContext ctx,
      Socket sock,
      byte[] authenticationToken,
      Function<Client, Future<?>> initCallback)
      throws IOException {
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    InputStream rawInput = sock.getInputStream();
    OutputStream output = sock.getOutputStream();
    ProtocolNegotiator negotiator = new ProtocolNegotiator(config.getVersion());
    PushbackInputStream input = ProtocolNegotiator.createNegotiationStream(rawInput);

    // getClient runs synchronously on the single server accept thread. Without a read timeout a
    // client that connects but never completes the handshake (a crashed client, a port scanner,
    // or plain `nc`) blocks the accept loop forever, permanently preventing any new client from
    // attaching. Bound the negotiation + handshake reads; the timeout is restored to its previous
    // value before a live client (with its own dedicated reader thread) is handed off.
    int previousTimeout = sock.getSoTimeout();
    boolean timeoutRestored = false;
    try {
      sock.setSoTimeout(ProtocolNegotiator.getNegotiationTimeoutMs());
      if (authenticationToken != null) {
        ConnectionAuthenticator.authenticateAgent(rawInput, output, authenticationToken);
      }
      ProtocolVersion negotiated;
      if (config.isAutoNegotiate()) {
        negotiated = negotiator.negotiateAgent(input, output);
      } else if (config.getVersion() == ProtocolVersion.V2) {
        negotiated = negotiator.negotiateAgent(input, output);
        if (negotiated != ProtocolVersion.V2) {
          throw new IOException("Protocol negotiation failed: expected V2");
        }
      } else {
        negotiated = ProtocolVersion.V1;
      }

      WireProtocol wireProtocol =
          negotiated == ProtocolVersion.V2
              ? new BinaryWireProtocol(input, output)
              : new JavaSerializationProtocol(input, output);
      SharedSettings settings = ctx.getSettings();

      while (true) {
        Command cmd;
        try {
          cmd = wireProtocol.read();
        } catch (ClassNotFoundException e) {
          throw new IOException(e);
        }
        switch (cmd.getType()) {
          case Command.SET_PARAMS:
            {
              settings.from(((SetSettingsCommand) cmd).getParams());
              break;
            }
          case Command.INSTRUMENT:
            {
              log.debug("got instrument command");
              try {
                // Restore the blocking timeout before the client's dedicated reader thread starts.
                sock.setSoTimeout(previousTimeout);
                timeoutRestored = true;
                Client client = new RemoteClient(ctx, wireProtocol, sock, (InstrumentCommand) cmd);
                initCallback.apply(client).get();
                client.sendCommand(new StatusCommand(StatusCommand.STATUS_FLAG));
                return client;
              } catch (ExecutionException | InterruptedException e) {
                wireProtocol.write(new StatusCommand(-1 * StatusCommand.STATUS_FLAG));
                throw new IOException(e);
              }
            }
          case Command.RECONNECT:
            {
              String probeId = ((ReconnectCommand) cmd).getProbeId();
              log.debug("Attempting to reconnect client for probe {}", probeId);
              Client client = Client.findClient(probeId);
              log.debug("Found client {}", client);
              if (client instanceof RemoteClient) {
                sock.setSoTimeout(previousTimeout);
                timeoutRestored = true;
                ((RemoteClient) client).reconnect(wireProtocol, sock);
                client.sendCommand(new StatusCommand(ReconnectCommand.STATUS_FLAG));
                return client;
              }
              wireProtocol.write(new StatusCommand(-1 * ReconnectCommand.STATUS_FLAG));
              throw new IOException("Can not reconnect to non-remote session");
            }
          case Command.LIST_PROBES:
            {
              ListProbesCommand listProbesCommand = (ListProbesCommand) cmd;
              listProbesCommand.setProbes(Client.listProbes());
              wireProtocol.write(listProbesCommand);
              break;
            }
          case Command.LIST_FAILED_EXTENSIONS:
            {
              ListFailedExtensionsCommand listFailedCmd = (ListFailedExtensionsCommand) cmd;
              listFailedCmd.setFailedExtensions(ExtensionRegistry.getFailedExtensions());
              wireProtocol.write(listFailedCmd);
              break;
            }
          case Command.EXIT:
            {
              return null;
            }
          default:
            {
              throw new IOException(
                  "expecting instrument, reconnect or settings command! (" + cmd.getClass() + ")");
            }
        }
      }
    } finally {
      if (authenticationToken != null) {
        Arrays.fill(authenticationToken, (byte) 0);
      }
      // Restore the original timeout on any exit path that did not hand off a live client
      // (negotiation failure, EXIT, or an unexpected command). The socket is typically closed
      // by the caller on these paths, so this is best-effort.
      if (!timeoutRestored) {
        try {
          sock.setSoTimeout(previousTimeout);
        } catch (IOException ignore) {
          // socket already closed - nothing to restore
        }
      }
    }
  }

  private RemoteClient(ClientContext ctx, WireProtocol protocol, Socket sock, InstrumentCommand cmd)
      throws IOException {
    super(ctx);
    this.sock = sock;
    this.protocol = protocol;
    terminalExitFinalizer = null;
    this.settings.from(ctx.getSettings());
    Class<?> btraceClazz = loadClass(cmd);
    if (btraceClazz == null) {
      throw new RuntimeException("can not load BTrace class");
    }

    initClient();
  }

  private RemoteClient(
      ClientContext ctx, WireProtocol protocol, Socket sock, IntConsumer terminalExitFinalizer) {
    super(ctx);
    this.sock = sock;
    this.protocol = protocol;
    this.terminalExitFinalizer = terminalExitFinalizer;
  }

  static RemoteClient createForTerminalTest(
      ClientContext ctx,
      WireProtocol protocol,
      Socket sock,
      BTraceRuntime.Impl runtime,
      IntConsumer terminalExitFinalizer) {
    RemoteClient client = new RemoteClient(ctx, protocol, sock, terminalExitFinalizer);
    client.setRuntimeForTest(runtime);
    client.initClient();
    return client;
  }

  private void initClient() {
    BTraceRuntime.initUnsafe();
    Thread cmdHandler =
        new Thread(
            () -> {
              try {
                BTraceRuntime.enter();
                while (true) {
                  try {
                    if (protocol == null) {
                      LockSupport.parkNanos(500_000_000L); // sleep 500ms
                      continue;
                    }
                    Command cmd = protocol.read();
                    switch (cmd.getType()) {
                      case Command.EXIT:
                        {
                          log.debug("received exit command");
                          TerminalHandshake handshake = beginTerminalHandshake();
                          BTraceRuntime.Impl rt = getRuntime();
                          if (rt != null) {
                            rt.handleExit(((ExitCommand) cmd).getExitCode());
                          }
                          try {
                            handshake.awaitCompletion();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }

                          return;
                        }
                      case Command.DISCONNECT:
                        {
                          log.debug("received disconnect command");
                          onCommand(cmd);
                          break;
                        }
                      case Command.LIST_PROBES:
                        {
                          onCommand(cmd);
                          break;
                        }
                      case Command.LIST_FAILED_EXTENSIONS:
                        {
                          onCommand(cmd);
                          break;
                        }
                      case Command.EVENT:
                        {
                          BTraceRuntime.Impl rt = getRuntime();
                          if (rt != null) {
                            rt.handleEvent((EventCommand) cmd);
                          }
                          break;
                        }
                      default:
                        if (log.isDebugEnabled()) {
                          log.debug("received {}", cmd);
                        }
                        // ignore any other command
                    }
                  } catch (Exception exp) {
                    // When the client disconnects normally, ObjectInputStream.read may throw
                    // EOFException. Treat it as a clean shutdown and avoid noisy stack traces
                    // that end up in target stderr during debug runs.
                    if (exp instanceof java.io.EOFException || exp instanceof SocketException) {
                      if (log.isDebugEnabled()) {
                        log.debug("client command stream closed: {}", exp.toString());
                      }
                    } else {
                      log.debug("Error while processing BTrace command", exp);
                    }
                    break;
                  }
                }
              } finally {
                BTraceRuntime.leave();
                if (terminalHandshake.get() == null) {
                  try {
                    // A normal peer close owns its regular transport cleanup. A pending terminal
                    // handshake deliberately retains output until the generated Exit is written.
                    closeAll();
                  } catch (IOException ignore) {
                    // best effort
                  }
                }
              }
            });
    cmdHandler.setDaemon(true);
    log.debug("starting client command handler thread");
    cmdHandler.start();
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  public void onCommand(Command cmd) throws IOException {
    WireProtocol output = protocol;
    if (output == null) {
      if (!cmd.isUrgent()) {
        delayedCommands.add(cmd);
      }
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("client {}: got {}", getClassName(), cmd);
    }
    boolean isConnected = true;
    try {
      synchronized (output) {
        output.flush();
      }
    } catch (IOException e) {
      isConnected = false;
    }

    delayedCommands.forEach(new DelayedCommandExecutor(isConnected));

    if (!dispatchCommand(cmd, isConnected)) {
      if (!cmd.isUrgent()) {
        delayedCommands.add(cmd);
      }
    }
  }

  private boolean dispatchCommand(Command cmd, boolean isConnected) {
    if (cmd == Command.NULL) {
      return true; // do not dispatch the NULL command
    }
    WireProtocol output = protocol;
    Socket socket = sock;
    if (output == null) {
      return false;
    }
    try {
      switch (cmd.getType()) {
        case Command.EXIT:
          if (isConnected) {
            synchronized (output) {
              output.write(cmd);
              output.flush();
            }
          }
          completeTerminalExit(((ExitCommand) cmd).getExitCode());
          break;
        case Command.LIST_PROBES:
          {
            if (isConnected) {
              ((ListProbesCommand) cmd).setProbes(listProbes());
              output.write(cmd);
            }
            break;
          }
        case Command.LIST_FAILED_EXTENSIONS:
          {
            if (isConnected) {
              ((ListFailedExtensionsCommand) cmd)
                  .setFailedExtensions(ExtensionRegistry.getFailedExtensions());
              output.write(cmd);
            }
            break;
          }
        case Command.DISCONNECT:
          {
            ((DisconnectCommand) cmd).setProbeId(id.toString());
            synchronized (output) {
              output.write(cmd);
              output.flush();
              try {
                // Half-close the output to allow the client to read DISCONNECT reliably
                if (socket != null && !socket.isClosed()) {
                  socket.shutdownOutput();
                }
              } catch (IOException ioe) {
                // ignore; best effort
              }
            }
            if (log.isDebugEnabled()) {
              log.debug("sent DISCONNECT to client and shutdown socket output");
            }
            disconnected = true;
            break;
          }
        default:
          if (out != null) {
            if (cmd instanceof PrintableCommand) {
              ((PrintableCommand) cmd).print(out);
              break;
            }
          }
          if (isConnected) {
            output.write(cmd);
          }
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public boolean isDisconnected() {
    return disconnected;
  }

  private TerminalHandshake beginTerminalHandshake() {
    TerminalHandshake handshake = terminalHandshake.get();
    if (handshake != null) {
      return handshake;
    }
    TerminalHandshake created = new TerminalHandshake();
    return terminalHandshake.compareAndSet(null, created) ? created : terminalHandshake.get();
  }

  private void completeTerminalExit(int exitCode) {
    TerminalHandshake handshake = beginTerminalHandshake();
    if (!handshake.finalizationStarted.compareAndSet(false, true)) {
      return;
    }
    handshake.complete();
    // Client.onExit is finalization-only. The runtime generated and wrote the sole terminal Exit
    // above, so final agent cleanup cannot recurse into runtime shutdown or emit an echo.
    if (terminalExitFinalizer != null) {
      try {
        terminalExitFinalizer.accept(exitCode);
      } finally {
        try {
          closeAll();
        } catch (IOException e) {
          log.debug("Unable to close terminal test transport", e);
        }
      }
    } else {
      super.onExit(exitCode);
    }
  }

  @Override
  protected void sendCommand(Command command) {
    if (getRuntime() != null) {
      super.sendCommand(command);
      return;
    }
    // Runtime not yet initialized - send directly via protocol
    WireProtocol output = protocol;
    if (output != null) {
      try {
        synchronized (output) {
          output.write(command);
          output.flush();
        }
      } catch (IOException e) {
        log.warn("Failed to send command {} via protocol", command.getClass().getSimpleName(), e);
      }
    } else {
      log.warn(
          "Cannot send command {}, neither runtime nor protocol available",
          command.getClass().getSimpleName());
    }
  }

  @Override
  protected void closeAll() throws IOException {
    super.closeAll();
    disconnected = true;

    WireProtocol output = protocol;
    if (output != null) {
      output.close();
      protocolUpdater.compareAndSet(this, output, null);
    }
    Socket socket = sock;
    if (socket != null) {
      socket.close();
      sockUpdater.compareAndSet(this, socket, null);
    }
  }

  void reconnect(WireProtocol protocol, Socket socket) throws IOException {
    this.sock = socket;
    this.protocol = protocol;
    this.disconnected = false;
    onCommand(Command.NULL);
  }
}
