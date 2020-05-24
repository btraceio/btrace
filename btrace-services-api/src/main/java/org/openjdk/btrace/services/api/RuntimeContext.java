package org.openjdk.btrace.services.api;

import org.openjdk.btrace.core.comm.Command;

public interface RuntimeContext {
  void send(String msg);

  void send(Command message);
}
