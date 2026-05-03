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
import java.util.List;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.opensolaris.os.dtrace.DataEvent;
import org.opensolaris.os.dtrace.ProbeData;
import org.opensolaris.os.dtrace.Record;

/**
 * Command to represent data event from DTrace.
 *
 * @author A. Sundararajan
 */
public class DTraceDataCommand extends MessageCommand implements DTraceCommand {
  private DataEvent de;

  public DTraceDataCommand(DataEvent de) {
    super(asString(de), true);
    this.de = de;
  }

  /** Returns the underlying DTrace DataEvent. */
  public DataEvent getDataEvent() {
    return de;
  }

  public void write(ObjectOutput out) throws IOException {
    super.write(out);
    out.writeObject(de);
  }

  public void read(ObjectInput in) throws ClassNotFoundException, IOException {
    super.read(in);
    de = (DataEvent) in.readObject();
  }

  private static String asString(DataEvent de) {
    ProbeData pd = de.getProbeData();
    List<Record> records = pd.getRecords();
    StringBuilder buf = new StringBuilder();
    for (Record rec : records) {
      buf.append(rec);
    }
    return buf.toString();
  }
}
