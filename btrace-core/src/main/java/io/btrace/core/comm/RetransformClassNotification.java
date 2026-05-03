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
import java.io.PrintWriter;

/**
 * This command is sent out as a notification that a class is going to be transformed
 *
 * @author Jaroslav Bachorik jaroslav.bachorik@sun.com
 */
public class RetransformClassNotification extends Command implements PrintableCommand {
  private String className;

  public RetransformClassNotification(String className) {
    super(RETRANSFORM_CLASS, true);
    this.className = className;
  }

  public RetransformClassNotification() {
    super(RETRANSFORM_CLASS);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeObject(className);
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    className = (String) in.readObject();
  }

  public String getClassName() {
    return className;
  }

  @Override
  public void print(PrintWriter out) {
    out.append("Going to retransform class ").append(className).append('\n');
  }
}
