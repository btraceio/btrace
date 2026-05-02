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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @since WireIO v.1
 */
public class ListProbesCommand extends Command implements PrintableCommand {
  // CopyOnWriteArrayList ensures safe iteration during concurrent updates without CME
  private final List<String> probes = new CopyOnWriteArrayList<>();

  public ListProbesCommand() {
    super(Command.LIST_PROBES, true);
  }

  public void setProbes(Collection<String> probes) {
    this.probes.clear();
    if (probes != null && !probes.isEmpty()) {
      this.probes.addAll(probes);
    }
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(probes.size());
    for (String probe : probes) {
      out.writeUTF(probe);
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    int numProbes = in.readInt();
    for (int i = 0; i < numProbes; i++) {
      probes.add(in.readUTF());
    }
  }

  @Override
  public void print(PrintWriter out) {
    int cntr = 1;
    for (String probe : probes) {
      out.println(cntr++ + ": " + probe);
    }
  }
}
