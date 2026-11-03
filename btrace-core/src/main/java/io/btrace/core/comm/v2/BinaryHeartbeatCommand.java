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
package io.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Binary implementation of the HeartbeatCommand: a liveness check carrying an opaque nonce. */
public class BinaryHeartbeatCommand extends BinaryCommand {
  private long nonce;

  public BinaryHeartbeatCommand(long nonce) {
    super(HEARTBEAT, true);
    this.nonce = nonce;
  }

  public BinaryHeartbeatCommand() {
    this(0L);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeLong(out, nonce);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    nonce = BinaryProtocol.readLong(in);
  }

  public long getNonce() {
    return nonce;
  }
}
