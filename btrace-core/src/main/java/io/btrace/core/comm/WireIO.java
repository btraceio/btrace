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
