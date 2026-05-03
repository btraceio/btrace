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

import java.io.PrintStream;
import java.io.PrintWriter;

class RemoteException extends RuntimeException {
  private final String exceptionClass;
  private final String remoteStackTrace;

  RemoteException(String exceptionClass, String message, String remoteStackTrace) {
    super(message);
    this.exceptionClass = exceptionClass;
    this.remoteStackTrace = remoteStackTrace;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    if (remoteStackTrace != null) {
      s.print(remoteStackTrace);
      return;
    }
    super.printStackTrace(s);
  }

  @Override
  public void printStackTrace(PrintStream s) {
    if (remoteStackTrace != null) {
      s.print(remoteStackTrace);
      return;
    }
    super.printStackTrace(s);
  }

  @Override
  public String toString() {
    if (exceptionClass == null) {
      return super.toString();
    }
    String message = getMessage();
    return message == null ? exceptionClass : exceptionClass + ": " + message;
  }
}
