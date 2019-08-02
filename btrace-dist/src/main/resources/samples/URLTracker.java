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
import org.openjdk.btrace.core.annotations.DTraceRef;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.TLS;

import java.net.Proxy;
import java.net.URL;

import static org.openjdk.btrace.core.BTraceUtils.*;

/*
 * This sample prints every Java URL openURL and
 * openConnection (successful) attempts. In addition,
 * on platforms where DTrace is available, it runs
 * the D-script jurls.d -- which collects a histogram
 * of URL accesses by a btrace:::event probe. From this
 * BTrace program we raise that DTrace probe (dtraceProbe
 * call). Note that it is possible to do similar histogram
 * in BTrace itself (see Histogram.java). But, this sample
 * shows DTrace/BTrace integration as well. On exit, all
 * DTrace aggregates are printed by BTrace (i.e., the ones
 * that are not explicitly printed by DTrace printa call).
 */
@DTraceRef("jurls.d")
@BTrace
public class URLTracker {
    @TLS
    private static URL url;

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection"
    )
    public static void openURL(URL self) {
        url = self;
    }

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection"
    )
    public static void openURL(URL self, Proxy p) {
        url = self;
    }

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection",
            location = @Location(Kind.RETURN)
    )
    public static void openURL() {
        if (url != null) {
            println("open " + url);
            D.probe("java-url-open", Strings.str(url));
            url = null;
        }
    }
}
