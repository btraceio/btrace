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

/**
 * A simple data command that has one number value.
 *
 * @author A. Sundararajan
 */
public class NumberDataCommand extends DataCommand {
  private Number value;

  public NumberDataCommand() {
    this(null, 0);
  }

  public NumberDataCommand(String name, Number value) {
    super(NUMBER, name, false);
    this.value = value;
  }

  @Override
  public void print(PrintWriter out) {
    if (name != null && !name.isEmpty()) {
      out.print(name);
      out.print(" = ");
    }
    out.println(value);
  }

  public Number getValue() {
    return value;
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(name != null ? name : "");
    out.writeObject(value);
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    name = in.readUTF();
    Object obj = in.readObject();
    if (obj != null && !(obj instanceof Number)) {
      throw new IOException("Invalid data type: expected Number, got " + obj.getClass().getName());
    }
    value = (Number) obj;
  }
}
