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
package io.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.CommandListener;

/**
 * A client wrapper that uses the binary protocol for communication. This provides a
 * high-performance alternative to the standard Java serialization.
 */
public class BinaryClient {
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private final CommandListener commandListener;
  private final ReentrantLock readLock = new ReentrantLock();
  private final ReentrantLock writeLock = new ReentrantLock();
  private volatile boolean closed = false;

  /** Create a new binary client with the specified streams and command listener */
  public BinaryClient(
      InputStream inputStream, OutputStream outputStream, CommandListener commandListener) {
    this.inputStream = inputStream;
    this.outputStream = outputStream;
    this.commandListener = commandListener;
  }

  /** Send an event command */
  public void sendEvent(String event) throws IOException {
    send(new BinaryEventCommand(event));
  }

  /** Send an exit command */
  public void sendExit(int exitCode) throws IOException {
    send(new BinaryExitCommand(exitCode));
  }

  /** Send an instrument command */
  public void sendInstrument(byte[] code, String[] args) throws IOException {
    send(new BinaryInstrumentCommand(code, args));
  }

  /** Send an instrument command */
  public void sendInstrument(byte[] code, Map<String, String> args) throws IOException {
    send(new BinaryInstrumentCommand(code, args));
  }

  /** Send a message command */
  public void sendMessage(String message, boolean urgent) throws IOException {
    send(new BinaryMessageCommand(message, urgent));
  }

  /** Send a binary command */
  public void send(BinaryCommand cmd) throws IOException {
    if (closed) {
      throw new IOException("Client is closed");
    }

    writeLock.lock();
    try {
      BinaryWireIO.write(outputStream, cmd);
    } finally {
      writeLock.unlock();
    }
  }

  /** Send an original BTrace command (will be converted to binary format) */
  public void send(Command cmd) throws IOException {
    send(CommandAdapter.toBinaryCommand(cmd));
  }

  /** Read and process commands in a loop */
  public void commandLoop() throws IOException {
    if (closed) {
      throw new IOException("Client is closed");
    }

    try {
      while (!closed) {
        BinaryCommand cmd = readCommand();
        Command btraceCmd = CommandAdapter.toBtraceCommand(cmd);
        commandListener.onCommand(btraceCmd);

        if (cmd.getType() == BinaryCommand.EXIT) {
          break;
        }
      }
    } catch (IOException e) {
      if (!closed) {
        throw e;
      }
    }
  }

  /** Read a single command from the input stream */
  public BinaryCommand readCommand() throws IOException {
    if (closed) {
      throw new IOException("Client is closed");
    }

    readLock.lock();
    try {
      return BinaryWireIO.read(inputStream);
    } finally {
      readLock.unlock();
    }
  }

  /** Close the client and all associated resources */
  public void close() throws IOException {
    if (closed) {
      return;
    }

    closed = true;

    if (inputStream != null) {
      inputStream.close();
    }

    if (outputStream != null) {
      outputStream.close();
    }
  }
}
