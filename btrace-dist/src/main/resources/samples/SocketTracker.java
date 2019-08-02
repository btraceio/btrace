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
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Return;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TLS;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import static org.openjdk.btrace.core.BTraceUtils.Strings;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This example tracks all server socket creations
 * and client socket accepts.
 * <br>
 * Also, it shows how to use shared methods.
 */
@BTrace
public class SocketTracker {
    @TLS
    private static int port = -1;
    @TLS
    private static InetAddress inetAddr;
    @TLS
    private static SocketAddress sockAddr;

    @OnMethod(
            clazz = "java.net.ServerSocket",
            method = "<init>"
    )
    public static void onServerSocket(@Self ServerSocket self,
                                      int p, int backlog, InetAddress bindAddr) {
        port = p;
        inetAddr = bindAddr;
    }

    @OnMethod(
            clazz = "java.net.ServerSocket",
            method = "<init>",
            type = "void (int, int, java.net.InetAddress)",
            location = @Location(Kind.RETURN)
    )
    public static void onSockReturn() {
        if (port != -1) {
            println("server socket at " + port);
            port = -1;
        }
        if (inetAddr != null) {
            println("server socket at " + inetAddr);
            inetAddr = null;
        }
    }

    @OnMethod(
            clazz = "java.net.ServerSocket",
            method = "bind"
    )
    public static void onBind(@Self ServerSocket self, SocketAddress addr, int backlog) {
        sockAddr = addr;
    }

    @OnMethod(
            clazz = "java.net.ServerSocket",
            method = "bind",
            type = "void (java.net.SocketAddress, int)",
            location = @Location(Kind.RETURN)
    )
    public static void onBindReturn() {
        socketBound();
    }

    @OnMethod(
            clazz = "sun.nio.ch.ServerSocketChannelImpl",
            method = "bind"
    )
    public static void onBind(@Self Object self, SocketAddress addr, int backlog) {
        sockAddr = addr;
    }

    @OnMethod(
            clazz = "sun.nio.ch.ServerSocketChannelImpl",
            method = "bind",
            type = "void (java.net.SocketAddress, int)",
            location = @Location(Kind.RETURN)
    )
    public static void onBindReturn2() {
        socketBound();
    }

    @OnMethod(
            clazz = "java.net.ServerSocket",
            method = "accept",
            location = @Location(Kind.RETURN)
    )
    public static void onAcceptReturn(@Return Socket sock) {
        clientSocketAcc(sock);
    }

    @OnMethod(
            clazz = "sun.nio.ch.ServerSocketChannelImpl",
            method = "socket",
            location = @Location(Kind.RETURN)
    )
    public static void onSocket(@Return ServerSocket ssock) {
        println(Strings.strcat("server socket at ", Strings.str(ssock)));
    }

    @OnMethod(
            clazz = "sun.nio.ch.ServerSocketChannelImpl",
            method = "accept",
            location = @Location(Kind.RETURN)
    )
    public static void onAcceptReturn(@Return SocketChannel sockChan) {
        clientSocketAcc(sockChan);
    }

    private static void socketBound() {
        if (sockAddr != null) {
            println("server socket bind " + sockAddr);
            sockAddr = null;
        }
    }

    private static void clientSocketAcc(Object obj) {
        if (obj != null) {
            println("client socket accept " + obj);
        }
    }
}
