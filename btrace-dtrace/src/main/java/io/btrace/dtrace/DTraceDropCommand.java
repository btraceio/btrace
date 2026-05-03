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
package io.btrace.dtrace;

import io.btrace.core.comm.MessageCommand;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opensolaris.os.dtrace.DropEvent;

/**
 * Command class that represents DTrace drop event.
 *
 * @author A. Sundararajan
 */
public class DTraceDropCommand extends MessageCommand implements DTraceCommand {
  private DropEvent de;

  public DTraceDropCommand(DropEvent de) {
    super(asString(de), true);
    this.de = de;
  }

  /** Returns the underlying DTrace drop event */
  public DropEvent getDropEvent() {
    return de;
  }

  public void write(ObjectOutput out) throws IOException {
    super.write(out);
    out.writeObject(out);
  }

  public void read(ObjectInput in) throws ClassNotFoundException, IOException {
    super.read(in);
    de = (DropEvent) in.readObject();
  }

  private static String asString(DropEvent de) {
    return de.getDrop().getDefaultMessage();
  }
}
