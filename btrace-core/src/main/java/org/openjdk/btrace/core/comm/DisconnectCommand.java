package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;

/**
 * @since WireIO v.1
 */
public final class DisconnectCommand extends Command implements PrintableCommand {
  private String probeId = "";

  public DisconnectCommand() {
    super(Command.DISCONNECT, true);
  }

  public DisconnectCommand(String probeId) {
    super(Command.DISCONNECT, true);
    this.probeId = probeId;
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(probeId);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    probeId = in.readUTF();
  }

  public void setProbeId(String probeId) {
    this.probeId = probeId;
  }

  @Override
  public void print(PrintWriter out) {
    out.println("BTrace Probe: " + probeId);
  }
}
