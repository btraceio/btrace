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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessageCommand extends DataCommand {
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss:SSS");
        }
    };

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

    @Override
    protected void write(ObjectOutput out) throws IOException {
        out.writeLong(time);
        byte[] bytes = msg != null ? msg.getBytes(StandardCharsets.UTF_8) : new byte[0];
        out.writeInt(bytes.length);
        if (bytes.length > 0) {
            out.write(bytes);
        }
    }

    @Override
    protected void read(ObjectInput in)
            throws ClassNotFoundException, IOException {
        time = in.readLong();
        int len = in.readInt();
        byte[] bytes = new byte[len];

        int ptr = 0;
        while (ptr < len) {
            ptr += in.read(bytes, ptr, len - ptr);
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
            out.print(msg);
        }
    }
}
