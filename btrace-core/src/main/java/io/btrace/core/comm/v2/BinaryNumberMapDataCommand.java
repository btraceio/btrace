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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binary implementation of the NumberMapDataCommand. This command is used to send numeric map data
 * from the BTrace agent to the client.
 */
public class BinaryNumberMapDataCommand extends BinaryDataCommand {
  private Map<String, Number> data = new LinkedHashMap<>();
  private static final NumberEncoding ENCODING =
      new NumberEncoding((byte) 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6);

  static {
    // Register this command type
    BinaryCommand.registerCommand(NUMBER_MAP, BinaryNumberMapDataCommand::new);
  }

  public BinaryNumberMapDataCommand(String name, Map<String, Number> data) {
    super(NUMBER_MAP, name);
    if (data != null) {
      this.data.putAll(data);
    }
  }

  public BinaryNumberMapDataCommand() {
    this(null, null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    // Write the name
    super.write(out);

    // Write the map size
    BinaryProtocol.writeInt(out, data.size());

    // Write each map entry
    for (Map.Entry<String, Number> entry : data.entrySet()) {
      BinaryProtocol.writeString(out, entry.getKey());
      ENCODING.writeNumber(out, entry.getValue());
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

      Number value = ENCODING.readNumber(in);
      data.put(key, value);
    }
  }

  public Map<String, Number> getData() {
    return new LinkedHashMap<>(data);
  }

  public void setData(Map<String, Number> data) {
    this.data.clear();
    if (data != null) {
      this.data.putAll(data);
    }
  }
}
