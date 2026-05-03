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
import io.btrace.core.ArgsMap;

/**
 * Binary implementation of the InstrumentCommand. This command is used to send BTrace code to the
 * target VM for instrumentation.
 */
public class BinaryInstrumentCommand extends BinaryCommand {
  private byte[] code;
  private ArgsMap args;

  static {
    // Register this command type
    BinaryCommand.registerCommand(INSTRUMENT, BinaryInstrumentCommand::new);
  }

  public BinaryInstrumentCommand(byte[] code, ArgsMap args) {
    super(INSTRUMENT, true);
    this.code = code;
    this.args = args;
  }

  public BinaryInstrumentCommand(byte[] code, String[] args) {
    this(code, new ArgsMap(args));
  }

  public BinaryInstrumentCommand(byte[] code, Map<String, String> args) {
    this(code, new ArgsMap(args));
  }

  public BinaryInstrumentCommand() {
    this(null, (Map<String, String>) null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    // Write code bytes
    BinaryProtocol.writeByteArray(out, code);

    // Write args count
    BinaryProtocol.writeInt(out, args.size());

    // Write args
    for (Map.Entry<String, String> e : args) {
      BinaryProtocol.writeString(out, e.getKey());
      BinaryProtocol.writeString(out, e.getValue() != null ? e.getValue() : "");
    }
  }

  @Override
  protected void read(InputStream in) throws IOException {
    // Read code bytes
    code = BinaryProtocol.readByteArray(in);

    // Read args count
    int argsCount = BinaryProtocol.readInt(in);

    // Read args
    args = new ArgsMap(argsCount);
    for (int i = 0; i < argsCount; i++) {
      String key = BinaryProtocol.readString(in);
      String val = BinaryProtocol.readString(in);
      args.put(key, val);
    }
  }

  public byte[] getCode() {
    return code;
  }

  public ArgsMap getArguments() {
    return args;
  }
}
