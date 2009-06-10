/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.comm;

import java.io.Serializable;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

public abstract class Command implements Serializable {
    public static final byte ERROR      = 0;
    public static final byte EVENT      = 1;
    public static final byte EXIT       = 2;
    public static final byte INSTRUMENT = 3;
    public static final byte MESSAGE    = 4;
    public static final byte RENAME     = 5;
    public static final byte SUCCESS    = 6;
    public static final byte NUMBER_MAP = 7;
    public static final byte STRING_MAP = 8;
    public static final byte NUMBER     = 9;
    public static final byte GRID_DATA  = 10;
    
    
    public static final byte FIRST_COMMAND = ERROR;
    public static final byte LAST_COMMAND = GRID_DATA;

    protected byte type;
    protected Command(byte type) {
        if (type < FIRST_COMMAND || type > LAST_COMMAND) {
            throw new IllegalArgumentException();
        }
        this.type = type;
    }

    protected abstract void write(ObjectOutput out) throws IOException;
    protected abstract void read(ObjectInput in) 
        throws IOException, ClassNotFoundException;

    public byte getType() {
        return type;
    }
}
