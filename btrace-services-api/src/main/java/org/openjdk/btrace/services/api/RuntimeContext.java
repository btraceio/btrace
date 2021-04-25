package org.openjdk.btrace.services.api;

import org.openjdk.btrace.core.comm.Command;

/**
 * @deprecated Will be removed in BTrace 3
 */
@Deprecated
public interface RuntimeContext {
  void send(String msg);

  void send(Command message);
}
