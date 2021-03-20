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

public class WireIO {
  public static final int VERSION = 1;

  private WireIO() {}

  public static Command read(ObjectInput in) throws IOException {
    byte type = in.readByte();
    Command cmd;
    switch (type) {
      case Command.ERROR:
        cmd = new ErrorCommand();
        break;
      case Command.EVENT:
        cmd = new EventCommand();
        break;
      case Command.EXIT:
        cmd = new ExitCommand();
        break;
      case Command.INSTRUMENT:
        cmd = new InstrumentCommand();
        break;
      case Command.MESSAGE:
        cmd = new MessageCommand();
        break;
      case Command.RENAME:
        cmd = new RenameCommand();
        break;
      case Command.STATUS:
        cmd = new StatusCommand();
        break;
      case Command.NUMBER_MAP:
        cmd = new NumberMapDataCommand();
        break;
      case Command.STRING_MAP:
        cmd = new StringMapDataCommand();
        break;
      case Command.NUMBER:
        cmd = new NumberDataCommand();
        break;
      case Command.GRID_DATA:
        cmd = new GridDataCommand();
        break;
      case Command.RETRANSFORMATION_START:
        cmd = new RetransformationStartNotification();
        break;
      case Command.RETRANSFORM_CLASS:
        cmd = new RetransformClassNotification();
        break;
      case Command.SET_PARAMS:
        cmd = new SetSettingsCommand();
        break;
      case Command.LIST_PROBES:
        cmd = new ListProbesCommand();
        break;
      case Command.DISCONNECT:
        cmd = new DisconnectCommand();
        break;
      case Command.RECONNECT:
        cmd = new ReconnectCommand();
        break;
      default:
        throw new RuntimeException("invalid command: " + type);
    }
    try {
      cmd.read(in);
    } catch (ClassNotFoundException cnfe) {
      throw new IOException(cnfe);
    }
    return cmd;
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static void write(ObjectOutput out, Command cmd) throws IOException {
    synchronized (out) {
      out.writeByte(cmd.getType());
      cmd.write(out);
      if (cmd.isUrgent()) {
        out.flush();
      }
    }
  }
}
