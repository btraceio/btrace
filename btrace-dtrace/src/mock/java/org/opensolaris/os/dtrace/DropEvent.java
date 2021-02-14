package org.opensolaris.os.dtrace;

import java.util.EventObject;

@SuppressWarnings("SameReturnValue")
public class DropEvent extends EventObject {
  public DropEvent(Object source) {
    super(source);
  }

  public Drop getDrop() {
    return null;
  }
}
