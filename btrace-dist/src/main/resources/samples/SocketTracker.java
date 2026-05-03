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


import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Return;
import io.btrace.core.annotations.Self;
import io.btrace.core.annotations.TLS;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import static io.btrace.core.BTraceUtils.Strings;
import static io.btrace.core.BTraceUtils.println;

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
