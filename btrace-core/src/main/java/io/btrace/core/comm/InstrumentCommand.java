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
package io.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import io.btrace.core.ArgsMap;

public class InstrumentCommand extends Command {

  private byte[] code;
  private ArgsMap args;

  public InstrumentCommand(byte[] code, ArgsMap args) {
    super(INSTRUMENT, true);
    // Defensive copy of bytecode array to prevent external modification
    this.code = code != null ? code.clone() : null;
    // Create new ArgsMap by copying entries to prevent external modification
    if (args != null) {
      this.args = new ArgsMap(args.size());
      for (Map.Entry<String, String> entry : args) {
        this.args.put(entry.getKey(), entry.getValue());
      }
    } else {
      this.args = null;
    }
  }

  public InstrumentCommand(byte[] code, String[] args) {
    this(code, new ArgsMap(args));
  }

  public InstrumentCommand(byte[] code, Map<String, String> args) {
    this(code, new ArgsMap(args));
  }

  protected InstrumentCommand() {
    this(null, (Map<String, String>) null);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(code.length);
    out.write(code);
    out.writeInt(args.size());
    for (Map.Entry<String, String> e : args) {
      out.writeUTF(e.getKey());
      String val = e.getValue();
      out.writeUTF(val != null ? val : "");
    }
  }

  @Override
  protected void read(ObjectInput in) throws IOException {
    int len = in.readInt();
    code = new byte[len];
    in.readFully(code);
    len = in.readInt();
    args = new ArgsMap(len);
    for (int i = 0; i < len; i++) {
      String key = in.readUTF();
      String val = in.readUTF();
      args.put(key, val);
    }
  }

  public byte[] getCode() {
    return code != null ? code.clone() : null;
  }

  public ArgsMap getArguments() {
    return args;
  }
}
