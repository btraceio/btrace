/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
