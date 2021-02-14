package org.opensolaris.os.dtrace;

import java.util.EventObject;

@SuppressWarnings("SameReturnValue")
public class DataEvent extends EventObject {
  public DataEvent(Object source) {
    super(source);
  }

  public ProbeData getProbeData() {
    return null;
  }
}
