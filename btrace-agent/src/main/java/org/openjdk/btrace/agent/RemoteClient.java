/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.btrace.core.*;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.DisconnectCommand;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.BinaryWireProtocol;
import org.openjdk.btrace.core.comm.JavaSerializationProtocol;
import org.openjdk.btrace.core.comm.ListFailedExtensionsCommand;
import org.openjdk.btrace.core.comm.ListProbesCommand;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.core.comm.ProtocolConfig;
import org.openjdk.btrace.core.comm.ProtocolNegotiator;
import org.openjdk.btrace.core.comm.ProtocolVersion;
import org.openjdk.btrace.core.comm.ReconnectCommand;
import org.openjdk.btrace.core.comm.SetSettingsCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.WireProtocol;
import org.openjdk.btrace.extension.ExtensionRegistry;
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

  static Client getClient(ClientContext ctx, Socket sock, Function<Client, Future<?>> initCallback)
      throws IOException {
    SharedSettings settings = ctx.getSettings();
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    InputStream rawInput = sock.getInputStream();
    OutputStream output = sock.getOutputStream();
    ProtocolNegotiator negotiator = new ProtocolNegotiator(config.getVersion());
    PushbackInputStream input = ProtocolNegotiator.createNegotiationStream(rawInput);
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
  }

  private RemoteClient(
      ClientContext ctx,
      WireProtocol protocol,
      Socket sock,
      InstrumentCommand cmd)
      throws IOException {
    super(ctx);
    this.sock = sock;
    this.protocol = protocol;
    this.settings.from(ctx.getSettings());
    Class<?> btraceClazz = loadClass(cmd);
    if (btraceClazz == null) {
      throw new RuntimeException("can not load BTrace class");
    }

    initClient();
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
                          onCommand(cmd);

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
                          getRuntime().handleEvent((EventCommand) cmd);
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
                try {
                  // Ensure streams and socket are closed once the client side closed first.
                  closeAll();
                } catch (IOException ignore) {
                  // best effort
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
            output.write(cmd);
          }
          onExit(((ExitCommand) cmd).getExitCode());
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
              ((ListFailedExtensionsCommand) cmd).setFailedExtensions(
                  ExtensionRegistry.getFailedExtensions());
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
