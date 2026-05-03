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
package io.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ExitCommand extends Command {
  private int exitCode;

  public ExitCommand(int exitCode) {
    super(EXIT, true);
    this.exitCode = exitCode;
  }

  protected ExitCommand() {
    this(0);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(exitCode);
  }

  @Override
  protected void read(ObjectInput in) throws IOException {
    exitCode = in.readInt();
  }

  public int getExitCode() {
    return exitCode;
  }
}
