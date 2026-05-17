/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

/**
 * Binary implementation of the ReconnectCommand. This command is used to reconnect to a running
 * BTrace agent.
 */
public class BinaryReconnectCommand extends BinaryStringCommand {
  public static final int STATUS_FLAG = 8;

  static {
    // Register this command type
    BinaryCommand.registerCommand(RECONNECT, BinaryReconnectCommand::new);
  }

  public BinaryReconnectCommand(String probeId) {
    super(RECONNECT, probeId);
  }

  public BinaryReconnectCommand() {
    this(null);
  }

  public String getProbeId() {
    return getPayload();
  }

  public void setProbeId(String probeId) {
    setPayload(probeId);
  }
}
