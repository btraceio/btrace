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
package org.openjdk.btrace.dtrace;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.opensolaris.os.dtrace.ErrorEvent;

/**
 * Command that represents error message from DTrace.
 *
 * @author A. Sundararajan
 */
public class DTraceErrorCommand extends ErrorCommand implements DTraceCommand {
  private ErrorEvent ee;

  public DTraceErrorCommand(Exception exp, ErrorEvent ee) {
    super(exp);
    this.ee = ee;
  }

  /** Returns the underlying DTrace error event. */
  public ErrorEvent getErrorEvent() {
    return ee;
  }

  public void write(ObjectOutput out) throws IOException {
    super.write(out);
    out.writeObject(ee);
  }

  public void read(ObjectInput in) throws ClassNotFoundException, IOException {
    super.read(in);
    ee = (ErrorEvent) in.readObject();
  }
}
