/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import io.btrace.core.types.AnyType;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnProbe;
import io.btrace.core.annotations.Return;
import io.btrace.core.annotations.Self;
import io.btrace.core.annotations.TLS;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;

import static io.btrace.BTrace.println;

/**
 * This example tracks all server socket creations
 * and client socket accepts. Unlike SockerTracker.java,
 * this script uses only public API classes and @OnProbe
 * probes - which would be mapped to internal implementation
 * classes by a XML descriptor at BTrace agent. For this
 * sample, XML probe descriptor is "java.net.socket.xml".
 */
@BTrace
public class SocketTracker1 {
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

    @OnProbe(
            namespace = "java.net.socket",
            name = "server-socket-creator"
    )
    public static void onSocket(@Return ServerSocket ssock) {
        println("server socket at " + ssock);
    }

    @OnProbe(
            namespace = "java.net.socket",
            name = "bind"
    )
    public static void onBind(@Self Object self, SocketAddress addr, int backlog) {
        sockAddr = addr;
    }

    @OnProbe(
            namespace = "java.net.socket",
            name = "bind-return"
    )
    public static void onBindReturn() {
        if (sockAddr != null) {
            println("server socket bind " + sockAddr);
            sockAddr = null;
        }
    }

    @OnProbe(
            namespace = "java.net.socket",
            name = "accept-return"
    )
    public static void onAcceptReturn(AnyType sock) {
        if (sock != null) {
            println("client socket accept " + sock);
        }
    }
}
