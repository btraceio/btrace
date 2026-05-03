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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the RetransformationStartNotification. This command is used to notify
 * the client that class retransformation is about to start.
 */
public class BinaryRetransformationStartNotification extends BinaryCommand {
  private int numClasses;

  static {
    // Register this command type
    BinaryCommand.registerCommand(
        RETRANSFORMATION_START, BinaryRetransformationStartNotification::new);
  }

  public BinaryRetransformationStartNotification(int numClasses) {
    super(RETRANSFORMATION_START, true);
    this.numClasses = numClasses;
  }

  public BinaryRetransformationStartNotification() {
    this(0);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeInt(out, numClasses);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    numClasses = BinaryProtocol.readInt(in);
  }

  public int getNumClasses() {
    return numClasses;
  }

  public void setNumClasses(int numClasses) {
    this.numClasses = numClasses;
  }
}
