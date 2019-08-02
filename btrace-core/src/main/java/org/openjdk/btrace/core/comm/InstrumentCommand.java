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

import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

public class InstrumentCommand extends Command {
    private final DebugSupport debug;
    private byte[] code;
    private ArgsMap args;

    public InstrumentCommand(byte[] code, ArgsMap args, DebugSupport debug) {
        super(INSTRUMENT);
        this.code = code;
        this.args = args;
        this.debug = debug;
    }

    public InstrumentCommand(byte[] code, String[] args, DebugSupport debug) {
        this(code, new ArgsMap(args, debug), debug);
    }

    public InstrumentCommand(byte[] code, Map<String, String> args, DebugSupport debug) {
        this(code, new ArgsMap(args, debug), debug);
    }

    protected InstrumentCommand() {
        this(null, (Map<String, String>) null, new DebugSupport(SharedSettings.GLOBAL));
    }

    @Override
    protected void write(ObjectOutput out) throws IOException {
        out.writeInt(code.length);
        out.write(code);
        out.writeInt(args.size());
        for (Map.Entry<String, String> e : args) {
            out.writeUTF(e.getKey());
            String val = e.getValue();
            out.writeUTF(val != null ? val : "");
        }
    }

    @Override
    protected void read(ObjectInput in) throws IOException {
        int len = in.readInt();
        code = new byte[len];
        in.readFully(code);
        len = in.readInt();
        args = new ArgsMap(len, debug);
        for (int i = 0; i < len; i++) {
            String key = in.readUTF();
            String val = in.readUTF();
            args.put(key, val);
        }
    }

    public byte[] getCode() {
        return code;
    }

    public ArgsMap getArguments() {
        return args;
    }
}
