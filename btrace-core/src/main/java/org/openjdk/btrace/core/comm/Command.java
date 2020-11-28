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
import java.io.Serializable;

public abstract class Command implements Serializable {
  public static final byte ERROR = 0;
  public static final byte EVENT = 1;
  public static final byte EXIT = 2;
  public static final byte INSTRUMENT = 3;
  public static final byte MESSAGE = 4;
  public static final byte RENAME = 5;
  public static final byte STATUS = 6;
  public static final byte NUMBER_MAP = 7;
  public static final byte STRING_MAP = 8;
  public static final byte NUMBER = 9;
  public static final byte GRID_DATA = 10;
  public static final byte RETRANSFORMATION_START = 11;
  public static final byte RETRANSFORM_CLASS = 12;
  public static final byte SET_PARAMS = 13;
  public static final byte LIST_PROBES = 14;
  public static final byte DISCONNECT = 15;
  public static final byte RECONNECT = 16;

  public static final byte FIRST_COMMAND = ERROR;
  public static final byte LAST_COMMAND = RECONNECT;

  public static Command NULL = new Command() {
    @Override
    protected void write(ObjectOutput out) throws IOException {}

    @Override
    protected void read(ObjectInput in) throws IOException, ClassNotFoundException {}
  };

  protected byte type;
  private boolean urgent;

  protected Command(byte type) {
    this(type, true);
  }

  protected Command(byte type, boolean urgent) {
    if (type < FIRST_COMMAND || type > LAST_COMMAND) {
      throw new IllegalArgumentException();
    }
    this.type = type;
    this.urgent = urgent;
  }

  private Command() {
    this.type = -1;
    this.urgent = true;
  }

  protected abstract void write(ObjectOutput out) throws IOException;

  protected abstract void read(ObjectInput in) throws IOException, ClassNotFoundException;

  public final byte getType() {
    return type;
  }

  public final boolean isUrgent() {
    return urgent;
  }

  final void setUrgent() {
    urgent = true;
  }
}
