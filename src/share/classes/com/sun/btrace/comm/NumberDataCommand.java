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

package com.sun.btrace.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;

/**
 * A simple data command that has one number value.
 * 
 * @author A. Sundararajan
 */
public class NumberDataCommand extends DataCommand {
    private Number value;
    
    public NumberDataCommand() {
        this(null, 0);
    }
    
    public NumberDataCommand(String name, Number value) {
        super(NUMBER, name);
        this.value = value;
    }
    
    public void print(PrintWriter out) {
        if (name != null && !name.equals("")) {
            out.print(name);
            out.print(" = ");
        }
        out.println(value);
    }

    public Number getValue() {
        return value;
    }
    
    protected void write(ObjectOutput out) throws IOException {
        out.writeUTF(name != null ? name : "");
        out.writeObject(value);
    }

    protected void read(ObjectInput in)
            throws IOException, ClassNotFoundException {
        this.name = in.readUTF();
        this.value = (Number) in.readObject();
    }
}
