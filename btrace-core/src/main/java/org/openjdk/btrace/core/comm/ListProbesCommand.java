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
