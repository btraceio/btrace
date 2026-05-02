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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binary implementation of the StringMapDataCommand. This command is used to send string map data
 * from the BTrace agent to the client.
 */
public class BinaryStringMapDataCommand extends BinaryDataCommand {
  private Map<String, String> data = new LinkedHashMap<>();

  static {
    // Register this command type
    BinaryCommand.registerCommand(STRING_MAP, BinaryStringMapDataCommand::new);
  }

  public BinaryStringMapDataCommand(String name, Map<String, String> data) {
    super(STRING_MAP, name);
    if (data != null) {
      this.data.putAll(data);
    }
  }

  public BinaryStringMapDataCommand() {
    this(null, null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    // Write the name
    super.write(out);

    // Write the map size
    BinaryProtocol.writeInt(out, data.size());

    // Write each map entry
    for (Map.Entry<String, String> entry : data.entrySet()) {
      BinaryProtocol.writeString(out, entry.getKey());
      BinaryProtocol.writeString(out, entry.getValue());
    }
  }

  @Override
  protected void read(InputStream in) throws IOException {
    // Read the name
    super.read(in);

    // Read the map size
    int size = BinaryProtocol.readInt(in);

    // Clear any existing data
    data.clear();

    // Read each map entry
    for (int i = 0; i < size; i++) {
      String key = BinaryProtocol.readString(in);
      String value = BinaryProtocol.readString(in);
      data.put(key, value);
    }
  }

  public Map<String, String> getData() {
    return new LinkedHashMap<>(data);
  }

  public void setData(Map<String, String> data) {
    this.data.clear();
    if (data != null) {
      this.data.putAll(data);
    }
  }
}
