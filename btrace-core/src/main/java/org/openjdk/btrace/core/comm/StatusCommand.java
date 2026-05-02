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
package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class StatusCommand extends Command {
  public static final int STATUS_FLAG = 1;
  // custom flag
  private int flag;

  public StatusCommand(int flag) {
    super(STATUS, true);
    this.flag = flag;
  }

  public StatusCommand() {
    this((byte) 0);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(flag);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    flag = in.readInt();
  }

  public int getFlag() {
    return Math.abs(flag);
  }

  public boolean isSuccess() {
    return flag >= 0;
  }
}
