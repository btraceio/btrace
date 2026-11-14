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

public class ErrorCommand extends Command implements PrintableCommand {
  private String errorMessage;
  private String exceptionClass;
  private String stackTrace;
  private Throwable cause;

  public ErrorCommand(Throwable cause) {
    super(ERROR, true);
    this.cause = cause;
    if (cause != null) {
      this.errorMessage = cause.getMessage();
      this.exceptionClass = cause.getClass().getName();
      StringBuilder sb = new StringBuilder();
      for (StackTraceElement ste : cause.getStackTrace()) {
        sb.append(ste.toString()).append("\n");
      }
      this.stackTrace = sb.toString();
    }
  }

  protected ErrorCommand() {
    this(null);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(exceptionClass != null ? exceptionClass : "");
    out.writeUTF(errorMessage != null ? errorMessage : "");
    out.writeUTF(stackTrace != null ? stackTrace : "");
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    exceptionClass = in.readUTF();
    errorMessage = in.readUTF();
    stackTrace = in.readUTF();
    if (exceptionClass.isEmpty() && errorMessage.isEmpty() && stackTrace.isEmpty()) {
      cause = null;
    } else {
      RuntimeException re = new RuntimeException(
          exceptionClass + (errorMessage.isEmpty() ? "" : ": " + errorMessage));
      if (!stackTrace.isEmpty()) {
        re.addSuppressed(new Exception("Origin stack:\n" + stackTrace));
      }
      cause = re;
    }
  }

  public Throwable getCause() {
    return cause;
  }

  @Override
  public void print(PrintWriter out) {
    out.append("! ERROR\n");
    Throwable cause = getCause();
    if (cause != null) {
      cause.printStackTrace(out);
    } else {
      out.append("(No exception information available)\n");
    }
  }
}
