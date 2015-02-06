/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
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

package traces;

import resources.services.DummyRuntimService;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Injected;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.ServiceType;
import com.sun.btrace.services.api.Service;
import resources.services.DummySimpleService;

/**
 * Sanity test to make sure the injected services are properly intialized
 * and referenced further on.
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class ServicesTest {
    @Injected(ServiceType.RUNTIME)
    private static DummyRuntimService printer;

    @OnMethod(clazz = "resources.OnMethodTest", method="args")
    public static void testRuntimeService(String a, long b, String[] c, int[] d) {
        DummyRuntimService ds = Service.runtime(DummyRuntimService.class);

        ds.doit(10, "hello");
    }

    @OnMethod(clazz = "resources.OnMethodTest", method="noargs")
    public static void testSimpleService() {
        DummySimpleService ds = Service.simple(DummySimpleService.class);

        ds.doit("hello", 10);
    }

    @OnMethod(clazz = "resources.OnMethodTest", method="args$static")
    public static void testSingletonService(String a, long b, String[] c, int[] d) {
        DummySimpleService ds = Service.simple("getInstance", DummySimpleService.class);

        ds.doit("hello", 10);
    }

    @OnMethod(clazz = "resources.OnMethodTest", method="noargs$static")
    public static void testFieldInjection(@ProbeClassName String pcn) {
        printer.doit(10, "hey");
        printer.doit(20, "ho");
    }
}
