package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @since WireIO v.1
 */
public class ReconnectCommand extends Command {
  public static final int STATUS_FLAG = 8;

  private String probeId;

  public ReconnectCommand() {
    super(Command.RECONNECT);
  }

  public ReconnectCommand(String probeId) {
    super(Command.RECONNECT);
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

  public String getProbeId() {
    return probeId;
  }
}
