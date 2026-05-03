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
 * Binary implementation of the RetransformClassNotification. This command is used to notify the
 * client about a class that's being retransformed.
 */
public class BinaryRetransformClassNotification extends BinaryCommand {
  private String className;
  private int index;
  private int total;

  static {
    // Register this command type
    BinaryCommand.registerCommand(RETRANSFORM_CLASS, BinaryRetransformClassNotification::new);
  }

  public BinaryRetransformClassNotification(String className, int index, int total) {
    super(RETRANSFORM_CLASS, true);
    this.className = className;
    this.index = index;
    this.total = total;
  }

  public BinaryRetransformClassNotification() {
    this(null, 0, 0);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    BinaryProtocol.writeString(out, className);
    BinaryProtocol.writeInt(out, index);
    BinaryProtocol.writeInt(out, total);
  }

  @Override
  protected void read(InputStream in) throws IOException {
    className = BinaryProtocol.readString(in);
    index = BinaryProtocol.readInt(in);
    total = BinaryProtocol.readInt(in);
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }
}
