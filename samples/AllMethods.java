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

package com.sun.btrace.samples;

import com.sun.btrace.annotations.*;
import com.sun.btrace.services.impl.Printer;

/**
 * This script traces method entry into every method of
 * every class in javax.swing package! Think before using
 * this script -- this will slow down your app significantly!!
 */
@BTrace public class AllMethods {
    @Injected(ServiceType.RUNTIME)
    private static Printer printer;

    @OnMethod(
        clazz="/javax\\.swing\\..*/",
        method="${m}"
    )
    public static void m(@Self Object o, @ProbeClassName String probeClass, @ProbeMethodName String probeMethod) {
        printer.println("this = " + o);
        printer.print("entered " + probeClass);
        printer.println("." + probeMethod);
    }
}
