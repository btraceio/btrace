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
package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;

/** Exception thrown when a command has invalid structure or data. */
public class MalformedCommandException extends IOException {
  private final byte commandType;

  public MalformedCommandException(byte commandType, String message) {
    super(String.format("Malformed command (type=%d): %s", commandType, message));
    this.commandType = commandType;
  }

  public MalformedCommandException(byte commandType, String message, Throwable cause) {
    super(String.format("Malformed command (type=%d): %s", commandType, message), cause);
    this.commandType = commandType;
  }

  public byte getCommandType() {
    return commandType;
  }
}
