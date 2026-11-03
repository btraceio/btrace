/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Liveness check on an idle connection. The client sends it when it has received nothing for a
 * while, and the agent answers with a heartbeat carrying the same nonce. Neither side passes a
 * heartbeat on to the probe's output.
 *
 * <p>Only the v2 (binary) protocol carries it: a v1 peer treats an unknown command type as a broken
 * stream, so it is never written to a v1 connection.
 */
public final class HeartbeatCommand extends Command {
  private long nonce;

  public HeartbeatCommand() {
    this(0L);
  }

  public HeartbeatCommand(long nonce) {
    super(Command.HEARTBEAT, true);
    this.nonce = nonce;
  }

  public long getNonce() {
    return nonce;
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    throw new IOException("HEARTBEAT is only supported by the v2 protocol");
  }

  @Override
  protected void read(ObjectInput in) throws IOException {
    throw new IOException("HEARTBEAT is only supported by the v2 protocol");
  }
}
