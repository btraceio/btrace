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
 * Abstract base class for commands that contain a single string payload. This is used for simple
 * commands like EventCommand, RenameCommand, etc.
 */
public abstract class BinaryStringCommand extends BinaryCommand {
  protected String payload;

  protected BinaryStringCommand(byte type, String payload) {
    super(type, true);
    this.payload = payload;
  }

  protected BinaryStringCommand(byte type) {
    this(type, null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeString(out, payload);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    payload = BinaryProtocol.readString(in);
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }
}
