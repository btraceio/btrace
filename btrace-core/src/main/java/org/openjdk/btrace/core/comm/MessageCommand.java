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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessageCommand extends DataCommand {
  private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss:SSS"));

  private long time;
  private String msg;

  public MessageCommand(long time, String msg) {
    this(time, msg, false);
  }

  public MessageCommand(long time, String msg, boolean urgent) {
    super(MESSAGE, null, urgent);
    this.time = time;
    this.msg = msg;
  }

  public MessageCommand(String msg) {
    this(msg, false);
  }

  public MessageCommand(String msg, boolean urgent) {
    this(0L, msg, urgent);
  }

  protected MessageCommand() {
    this(0L, null);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeBoolean(isUrgent());
    out.writeLong(time);
    byte[] bytes = msg != null ? msg.getBytes(StandardCharsets.UTF_8) : new byte[0];
    out.writeInt(bytes.length);
    if (bytes.length > 0) {
      out.write(bytes);
    }
  }

  @Override
  protected void read(ObjectInput in) throws ClassNotFoundException, IOException {
    if (in.readBoolean()) {
      setUrgent();
    }
    time = in.readLong();
    int len = in.readInt();
    byte[] bytes = new byte[len];

    int ptr = 0;
    while (ptr < len) {
      int bytesRead = in.read(bytes, ptr, len - ptr);
      if (bytesRead == -1) {
        throw new IOException("Unexpected end of stream");
      }
      ptr += bytesRead;
    }

    msg = new String(bytes, StandardCharsets.UTF_8);
  }

  public long getTime() {
    return time;
  }

  public String getMessage() {
    return msg;
  }

  @Override
  public void print(PrintWriter out) {
    if (time != 0L) {
      out.print(DATE_FORMAT.get().format(new Date(time)));
      out.print(" : ");
    }
    if (msg != null) {
      out.println(msg);
    }
    if (isUrgent()) {
      out.flush();
    }
  }
}
