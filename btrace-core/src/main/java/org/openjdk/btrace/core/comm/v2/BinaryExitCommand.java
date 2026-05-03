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
 * Binary implementation of the ExitCommand. This command is used to signal the BTrace agent to
 * exit.
 */
public class BinaryExitCommand extends BinaryCommand {
  private int exitCode;

  static {
    // Register this command type
    BinaryCommand.registerCommand(EXIT, BinaryExitCommand::new);
  }

  public BinaryExitCommand(int exitCode) {
    super(EXIT, true);
    this.exitCode = exitCode;
  }

  public BinaryExitCommand() {
    this(0);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeInt(out, exitCode);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    exitCode = BinaryProtocol.readInt(in);
  }

  public int getExitCode() {
    return exitCode;
  }

  public void setExitCode(int exitCode) {
    this.exitCode = exitCode;
  }
}
