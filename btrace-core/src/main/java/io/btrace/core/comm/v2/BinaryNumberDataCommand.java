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
 * Binary implementation of the NumberDataCommand. This command is used to send numeric data from
 * the BTrace agent to the client.
 */
public class BinaryNumberDataCommand extends BinaryDataCommand {
  private Number value;
  private static final NumberEncoding ENCODING =
      new NumberEncoding((byte) 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6);

  static {
    // Register this command type
    BinaryCommand.registerCommand(NUMBER, BinaryNumberDataCommand::new);
  }

  public BinaryNumberDataCommand(String name, Number value) {
    super(NUMBER, name);
    this.value = value;
  }

  public BinaryNumberDataCommand() {
    this(null, null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    // Write the name
    super.write(out);

    // Write the value type + payload via unified encoding
    ENCODING.writeNumber(out, value);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    // Read the name
    super.read(in);

    // Read the value via unified encoding
    value = ENCODING.readNumber(in);
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = value;
  }
}
