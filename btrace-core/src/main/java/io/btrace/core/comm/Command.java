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
  public static final byte LIST_FAILED_EXTENSIONS = 17;

  public static final byte FIRST_COMMAND = ERROR;
  public static final byte LAST_COMMAND = LIST_FAILED_EXTENSIONS;

  @SuppressWarnings("RedundantThrows")
  public static final Command NULL =
      new Command() {
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
    type = -1;
    urgent = true;
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
