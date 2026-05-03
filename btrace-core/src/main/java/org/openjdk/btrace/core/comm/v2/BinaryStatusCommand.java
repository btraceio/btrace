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

/**
 * Binary implementation of the StatusCommand. This command is used to communicate the status of
 * BTrace operations.
 */
public class BinaryStatusCommand extends BinaryCommand {
  // Status flag constants
  public static final int STATUS_FLAG = 1;

  private int flag;
  private boolean success;

  static {
    // Register this command type
    BinaryCommand.registerCommand(STATUS, BinaryStatusCommand::new);
  }

  public BinaryStatusCommand(int flag) {
    this(flag, true);
  }

  public BinaryStatusCommand(int flag, boolean success) {
    super(STATUS, true);
    this.flag = flag;
    this.success = success;
  }

  public BinaryStatusCommand() {
    this(0, false);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeInt(out, flag);
    BinaryProtocol.writeBoolean(out, success);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    flag = BinaryProtocol.readInt(in);
    success = BinaryProtocol.readBoolean(in);
  }

  public int getFlag() {
    return flag;
  }

  public void setFlag(int flag) {
    this.flag = flag;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }
}
