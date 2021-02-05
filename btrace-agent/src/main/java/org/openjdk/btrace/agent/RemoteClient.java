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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.CircularBuffer;
import org.openjdk.btrace.core.Function;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.DisconnectCommand;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.ListProbesCommand;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.core.comm.ReconnectCommand;
import org.openjdk.btrace.core.comm.SetSettingsCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.WireIO;

/**
 * Represents a remote client communicated by socket.
 *
 * @author A. Sundararajan
 */
class RemoteClient extends Client {
  private final class DelayedCommandExecutor implements Function<Command, Boolean> {
    private boolean isConnected;

    public DelayedCommandExecutor(boolean isConnected) {
      this.isConnected = isConnected;
    }

    @Override
    public Boolean apply(Command value) {
      return dispatchCommand(value, isConnected);
    }
  }

  private volatile Socket sock;
  private volatile ObjectInputStream ois;
  private volatile ObjectOutputStream oos;

  private final CircularBuffer<Command> delayedCommands = new CircularBuffer<>(5000);

  static Client getClient(ClientContext ctx, Socket sock, Function<Client, Future<?>> initCallback)
      throws IOException {
    ObjectInputStream ois = new ObjectInputStream(sock.getInputStream());
    ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
    SharedSettings settings = new SharedSettings();
    while (true) {
      Command cmd = WireIO.read(ois);
      switch (cmd.getType()) {
        case Command.SET_PARAMS:
          {
            settings.from(((SetSettingsCommand) cmd).getParams());
            break;
          }
        case Command.INSTRUMENT:
          {
            try {
              Client client =
                  new RemoteClient(ctx, ois, oos, sock, (InstrumentCommand) cmd, settings);
              initCallback.apply(client).get();
              WireIO.write(oos, new StatusCommand(InstrumentCommand.STATUS_FLAG));
              return client;
            } catch (ExecutionException | InterruptedException e) {
              WireIO.write(oos, new StatusCommand(-1 * InstrumentCommand.STATUS_FLAG));
              throw new IOException(e);
            }
          }
        case Command.RECONNECT:
          {
            Client client = Client.findClient(((ReconnectCommand) cmd).getProbeId());
            if (client instanceof RemoteClient) {
              ((RemoteClient) client).reconnect(ois, oos, sock);
              WireIO.write(oos, new StatusCommand(ReconnectCommand.STATUS_FLAG));
              return client;
            }
            WireIO.write(oos, new StatusCommand(-1 * ReconnectCommand.STATUS_FLAG));
            throw new IOException("Can not reconnect to non-remote session");
          }
        case Command.LIST_PROBES:
          {
            ListProbesCommand listProbesCommand = (ListProbesCommand) cmd;
            listProbesCommand.setProbes(Client.listProbes());
            WireIO.write(oos, listProbesCommand);
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
      ObjectInputStream ois,
      ObjectOutputStream oos,
      Socket sock,
      InstrumentCommand cmd,
      SharedSettings settings)
      throws IOException {
    super(ctx);
    this.sock = sock;
    this.ois = ois;
    this.oos = oos;
    this.settings.from(settings);
    debugPrint("got instrument command");
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
            new Runnable() {
              @Override
              public void run() {
                try {
                  BTraceRuntime.enter();
                  while (true) {
                    try {
                      if (RemoteClient.this.ois == null) {
                        LockSupport.parkNanos(500_000_000L); // sleep 500ms
                        continue;
                      }
                      Command cmd = WireIO.read(RemoteClient.this.ois);
                      switch (cmd.getType()) {
                        case Command.EXIT:
                          {
                            debugPrint("received exit command");
                            onCommand(cmd);

                            return;
                          }
                        case Command.DISCONNECT:
                          {
                            debugPrint("received disconnect command");
                            onCommand(cmd);
                            break;
                          }
                        case Command.LIST_PROBES:
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
                          if (isDebug()) {
                            debugPrint("received " + cmd);
                          }
                          // ignore any other command
                      }
                    } catch (Exception exp) {
                      debugPrint(exp);
                      break;
                    }
                  }
                } finally {
                  BTraceRuntime.leave();
                }
              }
            });
    cmdHandler.setDaemon(true);
    debugPrint("starting client command handler thread");
    cmdHandler.start();
  }

  @Override
  public void onCommand(Command cmd) throws IOException {
    if (oos == null) {
      if (!cmd.isUrgent()) {
        delayedCommands.add(cmd);
      }
      return;
    }
    if (isDebug()) {
      debugPrint("client " + getClassName() + ": got " + cmd);
    }
    try {
      boolean isConnected = true;
      try {
        synchronized (oos) {
          oos.reset();
        }
      } catch (SocketException e) {
        isConnected = false;
      }

      delayedCommands.forEach(new DelayedCommandExecutor(isConnected));

      if (!dispatchCommand(cmd, isConnected)) {
        if (!cmd.isUrgent()) {
          delayedCommands.add(cmd);
        }
      }

    } catch (IOException ignored) {
      // client can be in detached state
    }
  }

  private boolean dispatchCommand(Command cmd, boolean isConnected) {
    if (cmd == Command.NULL) {
      return true; // do not dispatch the NULL command
    }
    try {
      switch (cmd.getType()) {
        case Command.EXIT:
          if (isConnected) {
            WireIO.write(oos, cmd);
          }
          onExit(((ExitCommand) cmd).getExitCode());
          break;
        case Command.LIST_PROBES:
          {
            if (isConnected) {
              ((ListProbesCommand) cmd).setProbes(listProbes());
              WireIO.write(oos, cmd);
            }
            break;
          }
        case Command.DISCONNECT:
          {
            ((DisconnectCommand) cmd).setProbeId(id.toString());
            synchronized (oos) {
              WireIO.write(oos, cmd);
              oos.flush();
              ois.close();
              oos.close();
            }
            sock.close();
            this.ois = null;
            this.oos = null;
            this.sock = null;
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
            WireIO.write(oos, cmd);
          }
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public boolean isDisconnected() {
    return sock == null;
  }

  @Override
  protected synchronized void closeAll() throws IOException {
    super.closeAll();

    if (oos != null) {
      synchronized (oos) {
        oos.close();
      }
      oos = null;
    }
    if (ois != null) {
      ois.close();
      ois = null;
    }
    if (sock != null) {
      sock.close();
      sock = null;
    }
  }

  void reconnect(ObjectInputStream ois, ObjectOutputStream oos, Socket socket) throws IOException {
    this.sock = socket;
    this.ois = ois;
    this.oos = oos;
    onCommand(Command.NULL);
  }
}
