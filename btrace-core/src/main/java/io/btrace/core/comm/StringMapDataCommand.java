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
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A data command that hold data of type Map&lt;String, String&gt;.
 *
 * @author A. Sundararajan
 */
public class StringMapDataCommand extends DataCommand {

  private Map<String, String> data;

  public StringMapDataCommand() {
    this(null, null);
  }

  public StringMapDataCommand(String name, Map<String, String> data) {
    super(STRING_MAP, name, false);
    if (data != null) {
      this.data = new HashMap<>(data);
    } else {
      this.data = Collections.emptyMap();
    }
  }

  public Map<String, String> getData() {
    return data;
  }

  @Override
  public void print(PrintWriter out) {
    if (name != null && !name.isEmpty()) {
      out.println(name);
    }
    for (Map.Entry<String, String> e : data.entrySet()) {
      out.print(e.getKey());
      out.print(" = ");
      out.println(e.getValue());
    }
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(name != null ? name : "");
    out.writeInt(data.size());
    for (String key : data.keySet()) {
      out.writeUTF(key);
      out.writeUTF(data.get(key));
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    name = in.readUTF();
    data = new HashMap<>();
    int sz = in.readInt();
    for (int i = 0; i < sz; i++) {
      data.put(in.readUTF(), in.readUTF());
    }
  }
}
