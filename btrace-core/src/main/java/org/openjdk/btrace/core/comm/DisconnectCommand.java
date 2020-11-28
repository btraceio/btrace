package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public final class DisconnectCommand extends Command {
  public DisconnectCommand() {
    super(Command.DISCONNECT);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {}

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {}
}
