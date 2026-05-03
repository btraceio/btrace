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

import java.io.IOException;

/** Exception thrown when deserialization of a command fails. */
public class CommandDeserializationException extends IOException {
  private final byte commandType;

  public CommandDeserializationException(byte commandType, String message, Throwable cause) {
    super(
        String.format("Failed to deserialize command (type=%d): %s", commandType, message), cause);
    this.commandType = commandType;
  }

  public CommandDeserializationException(byte commandType, Throwable cause) {
    super(String.format("Failed to deserialize command (type=%d)", commandType), cause);
    this.commandType = commandType;
  }

  public byte getCommandType() {
    return commandType;
  }
}
