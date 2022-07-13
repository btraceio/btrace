/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
