/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
    this.params = params != null ? new HashMap<>(params) : new HashMap<String, Object>();
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
    for (int i = 0; i < size; i++) {
      String k = in.readUTF();
      Object v = in.readObject();

      params.put(k, v);
    }
  }
}
