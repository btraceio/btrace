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

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessageCommand extends DataCommand {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss:SSS");
    private long time;
    private String msg;

    public MessageCommand(long time, String msg) {
        super(MESSAGE, null);
        this.time = time;
        this.msg = msg;
    }

    public MessageCommand(String msg) {
        this(0L, msg);
    }

    protected MessageCommand() {
        this(0L, null);
    }
    
    protected void write(ObjectOutput out) throws IOException {
        out.writeLong(time);
        out.writeUTF(msg != null? msg : "");
    }

    protected void read(ObjectInput in) 
                   throws ClassNotFoundException, IOException {
        time = in.readLong();
        msg = in.readUTF();
    }

    public long getTime() {
        return time;
    }

    public String getMessage() {
        return msg;
    }
    
    public void print(PrintWriter out) {
        if (time != 0L) {
            out.print(DATE_FORMAT.format(new Date(time)));
            out.print(" : ");
        }
        if (msg != null) {
            out.print(msg);
        }
    }
}
