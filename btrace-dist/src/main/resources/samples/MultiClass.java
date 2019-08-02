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


import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.ProbeClassName;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This BTrace class demonstrates that we can
 * probe into multiple classes and methods by a
 * single probe specification using regular
 * expressions for class and/or method names as
 * given below. In the example, we put probe into
 * all readXXX methods of all InputStream classes.
 */
@BTrace
public class MultiClass {
    @OnMethod(
            clazz = "/java\\.io\\..*Input.*/",
            method = "/read.*/"
    )
    public static void onread(@ProbeClassName String pcn) {
        println("read on " + pcn);
    }
}
