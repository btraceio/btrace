/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.btrace.samples;

import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;

/**
 * This example demonstrates printing stack trace
 * of an exception and thread local variables. This
 * trace script prints exception stack trace whenever
 * java.lang.Throwable's constructor returns. This way
 * you can trace all exceptions that may be caught and
 * "eaten" silently by the traced program. Note that the
 * assumption is that the exceptions are thrown soon after
 * creation [like in "throw new FooException();"] rather
 * that be stored and thrown later.
 */
@BTrace public class OnThrow {    
    // store current exception in a thread local
    // variable (@TLS annotation). Note that we can't
    // store it in a global variable!
    @TLS static Throwable currentException;

    // introduce probe into every constructor of java.lang.Throwable
    // class and store "this" in the thread local variable.
    @OnMethod(
        clazz="java.lang.Throwable",
        method="<init>"
    )
    public static void onthrow(@Self Throwable self) {
        currentException = self;
    }

    @OnMethod(
        clazz="java.lang.Throwable",
        method="<init>"
    )
    public static void onthrow1(@Self Throwable self, String s) {
        currentException = self;
    }

    @OnMethod(
        clazz="java.lang.Throwable",
        method="<init>"
    )
    public static void onthrow1(@Self Throwable self, String s, Throwable cause) {
        currentException = self;
    }

    @OnMethod(
        clazz="java.lang.Throwable",
        method="<init>"
    )
    public static void onthrow2(@Self Throwable self, Throwable cause) {
        currentException = self;
    }

    // when any constructor of java.lang.Throwable returns
    // print the currentException's stack trace.
    @OnMethod(
        clazz="java.lang.Throwable",
        method="<init>",
        location=@Location(Kind.RETURN)
    )
    public static void onthrowreturn() {
        if (currentException != null) {
            Threads.jstack(currentException);
            println("=====================");
            currentException = null;
        }
    }
}
