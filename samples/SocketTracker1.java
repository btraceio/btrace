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
import static com.sun.btrace.BTraceUtils.Strings.*;
import java.net.*;
import com.sun.btrace.AnyType;

/**
 * This example tracks all server socket creations
 * and client socket accepts. Unlike SockerTracker.java,
 * this script uses only public API classes and @OnProbe
 * probes - which would be mapped to internal implementation
 * classes by a XML descriptor at BTrace agent. For this
 * sample, XML probe descriptor is "java.net.socket.xml".
 */
@BTrace public class SocketTracker1 {
    @TLS private static int port = -1;
    @TLS private static InetAddress inetAddr;
    @TLS private static SocketAddress sockAddr;

    @OnMethod(
        clazz="java.net.ServerSocket",
        method="<init>"
    )
    public static void onServerSocket(@Self ServerSocket self, 
        int p, int backlog, InetAddress bindAddr) {
        port = p;
        inetAddr = bindAddr;
    }

    @OnMethod(
        clazz="java.net.ServerSocket",
        method="<init>",
        type="void (int, int, java.net.InetAddress)",
        location=@Location(Kind.RETURN)
    )
    public static void onSockReturn() {
        if (port != -1) {
            println(Strings.strcat("server socket at ", Strings.str(port)));
            port = -1;
        }
        if (inetAddr != null) {
            println(Strings.strcat("server socket at ", Strings.str(inetAddr)));
            inetAddr = null;
        }
    }

    @OnProbe(
        namespace="java.net.socket",
        name="server-socket-creator"
    )
    public static void onSocket(@Return ServerSocket ssock) {
        println(Strings.strcat("server socket at ", Strings.str(ssock)));
    }

    @OnProbe(
        namespace="java.net.socket",
        name="bind"
    )
    public static void onBind(@Self Object self, SocketAddress addr, int backlog) {
        sockAddr = addr;
    }

    @OnProbe(
        namespace="java.net.socket",
        name="bind-return"
    )
    public static void onBindReturn() {
        if (sockAddr != null) {
            println(Strings.strcat("server socket bind ", Strings.str(sockAddr)));
            sockAddr = null;
        }
    }

    @OnProbe(
        namespace="java.net.socket",
        name="accept-return"
    )
    public static void onAcceptReturn(AnyType sock) {
        if (sock != null) {
            println(Strings.strcat("client socket accept ", Strings.str(sock)));
        }
    }
}
