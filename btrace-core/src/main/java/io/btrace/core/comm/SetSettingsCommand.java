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
import java.util.HashMap;
import java.util.Map;

/**
 * This command is used to remotely set custom settings (trusted mode, debug, etc.)
 *
 * @author Jaroslav Bachorik
 */
public class SetSettingsCommand extends Command {
  private final Map<String, Object> params;

  public SetSettingsCommand(Map<String, ?> params) {
    super(SET_PARAMS, true);
    this.params = params != null ? new HashMap<>(params) : new HashMap<>();
  }

  protected SetSettingsCommand() {
    this(null);
  }

  public Map<String, Object> getParams() {
    return params;
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(params.size());
    for (Map.Entry<String, ?> e : params.entrySet()) {
      out.writeUTF(e.getKey());
      TypedEncoding.writeValue(out, e.getValue());
    }
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    int size = in.readInt();
    if (size < 0 || size > TypedEncoding.MAX_PARAMS) {
      throw new IOException("Invalid params size: " + size);
    }
    for (int i = 0; i < size; i++) {
      String k = in.readUTF();
      if (k.length() > TypedEncoding.MAX_KEY_LEN) {
        throw new IOException("Settings key too long: " + k.length());
      }
      Object v = TypedEncoding.readValue(in);
      params.put(k, v);
    }
  }
}
