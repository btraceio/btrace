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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Binary implementation of the ListProbesCommand. This command is used to list active BTrace
 * probes.
 */
public class BinaryListProbesCommand extends BinaryCommand {
  private final List<String> probes = new ArrayList<>();

  static {
    // Register this command type
    BinaryCommand.registerCommand(LIST_PROBES, BinaryListProbesCommand::new);
  }

  public BinaryListProbesCommand() {
    super(LIST_PROBES, true);
  }

  public void setProbes(Collection<String> probes) {
    this.probes.clear();
    this.probes.addAll(probes);
  }

  public List<String> getProbes() {
    return new ArrayList<>(probes);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeInt(out, probes.size());
    for (String probe : probes) {
      BinaryProtocol.writeString(out, probe);
    }
  }

  @Override
  protected void read(InputStream in) throws IOException {
    int numProbes = BinaryProtocol.readInt(in);
    probes.clear();
    for (int i = 0; i < numProbes; i++) {
      probes.add(BinaryProtocol.readString(in));
    }
  }
}
