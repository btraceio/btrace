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
      out.writeObject(e.getValue());
    }
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    int size = in.readInt();
    if (size < 0 || size > 10000) { // Reasonable limit to prevent memory exhaustion
      throw new IOException("Invalid params size: " + size);
    }
    for (int i = 0; i < size; i++) {
      String k = in.readUTF();
      Object v = in.readObject();
      // Validate that value is a reasonable settings type
      if (v != null
          && !(v instanceof String)
          && !(v instanceof Number)
          && !(v instanceof Boolean)) {
        throw new IOException(
            "Invalid settings value type: expected String/Number/Boolean, got "
                + v.getClass().getName());
      }
      params.put(k, v);
    }
  }
}
