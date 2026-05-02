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
package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A data command that hold data of type Map&lt;String, Number&gt;.
 *
 * @author A. Sundararajan
 */
public class NumberMapDataCommand extends DataCommand {

  private Map<String, ? extends Number> data;

  public NumberMapDataCommand() {
    this(null, null);
  }

  public NumberMapDataCommand(String name, Map<String, ? extends Number> data) {
    super(NUMBER_MAP, name, false);
    this.data = (data != null) ? new HashMap<String, Number>(data) : null;
  }

  public Map<String, ? extends Number> getData() {
    return data;
  }

  @Override
  public void print(PrintWriter out) {
    if (name != null && !name.isEmpty()) {
      out.println(name);
    }
    if (data != null) {
      for (Map.Entry<String, ? extends Number> e : data.entrySet()) {
        out.print(e.getKey());
        out.print(" = ");
        out.println(e.getValue());
      }
    }
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(name != null ? name : "");
    if (data != null) {
      out.writeInt(data.size());
      for (String key : data.keySet()) {
        out.writeUTF(key);
        out.writeObject(data.get(key));
      }
    } else {
      out.writeInt(0);
    }
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    name = in.readUTF();
    Map<String, Number> map = new HashMap<>();
    int sz = in.readInt();
    for (int i = 0; i < sz; i++) {
      map.put(in.readUTF(), (Number) in.readObject());
    }
    data = map;
  }
}
