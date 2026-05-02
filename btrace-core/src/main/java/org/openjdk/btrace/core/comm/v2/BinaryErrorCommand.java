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
package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the ErrorCommand. This command is used to send error information from
 * the BTrace agent to the client.
 */
public class BinaryErrorCommand extends BinaryCommand {
  private int cause;
  private String message;

  static {
    // Register this command type
    BinaryCommand.registerCommand(ERROR, BinaryErrorCommand::new);
  }

  public BinaryErrorCommand(int cause, String message) {
    super(ERROR, true);
    this.cause = cause;
    this.message = message;
  }

  public BinaryErrorCommand() {
    this(0, null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeInt(out, cause);
    BinaryProtocol.writeString(out, message);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    cause = BinaryProtocol.readInt(in);
    message = BinaryProtocol.readString(in);
  }

  public int getCause() {
    return cause;
  }

  public void setCause(int cause) {
    this.cause = cause;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
