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
 * Binary implementation of the RenameCommand. This command is used to rename BTrace output files.
 */
public class BinaryRenameCommand extends BinaryStringCommand {
  static {
    // Register this command type
    BinaryCommand.registerCommand(RENAME, BinaryRenameCommand::new);
  }

  public BinaryRenameCommand(String newName) {
    super(RENAME, newName);
  }

  public BinaryRenameCommand() {
    this(null);
  }

  public String getNewName() {
    return getPayload();
  }

  public void setNewName(String newName) {
    setPayload(newName);
  }
}
