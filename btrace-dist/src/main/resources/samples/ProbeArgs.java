/*
 * Copyright (c) 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */


import org.openjdk.btrace.core.annotations.BTrace;

import static org.openjdk.btrace.core.BTraceUtils.Sys;
import static org.openjdk.btrace.core.BTraceUtils.println;

@BTrace(trusted = true)
public class ProbeArgs {
    static {
        println("arg#=" + Sys.$length());
        for (int i = 0; i < Sys.$length(); i++) {
            println("#" + i + "=" + Sys.$(i));
        }
        println("switch=" + Sys.$("switch"));
    }
}
