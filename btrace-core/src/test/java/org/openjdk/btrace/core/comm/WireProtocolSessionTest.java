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
package org.openjdk.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PushbackInputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class WireProtocolSessionTest {
  private interface SessionHandler {
    void handle(WireProtocol protocol) throws Exception;
  }

  @Test
  void testDisconnectThenListProbesAcrossConnections() throws Exception {
    String probeId = "probe-12345";
    String probeEntry = probeId + " [OnMethodTest]";

    runV2Session(
        protocol -> {
          Command cmd = protocol.read();
          assertTrue(cmd instanceof DisconnectCommand);
          protocol.write(new DisconnectCommand(probeId));
          protocol.flush();
        },
        protocol -> {
          protocol.write(new DisconnectCommand());
          protocol.flush();
          Command cmd = protocol.read();
          assertTrue(cmd instanceof DisconnectCommand);
          assertEquals(probeId, ((DisconnectCommand) cmd).getProbeId());
        });

    runV2Session(
        protocol -> {
          Command cmd = protocol.read();
          assertTrue(cmd instanceof ListProbesCommand);
          ListProbesCommand response = (ListProbesCommand) cmd;
          response.setProbes(Collections.singletonList(probeEntry));
          protocol.write(response);
          protocol.flush();
        },
        protocol -> {
          protocol.write(new ListProbesCommand());
          protocol.flush();
          Command cmd = protocol.read();
          assertTrue(cmd instanceof ListProbesCommand);
          List<String> probes = ((ListProbesCommand) cmd).getProbes();
          assertEquals(1, probes.size());
          assertEquals(probeEntry, probes.get(0));
        });
  }

  private static void runV2Session(SessionHandler serverHandler, SessionHandler clientHandler)
      throws Exception {
    Duplex duplex = new Duplex();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> serverFuture =
        executor.submit(
            () -> {
              try (WireProtocol protocol =
                  createServerProtocol(duplex.serverIn, duplex.serverOut)) {
                serverHandler.handle(protocol);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    try (WireProtocol protocol = createClientProtocol(duplex.clientIn, duplex.clientOut)) {
      clientHandler.handle(protocol);
    }

    try {
      serverFuture.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw unwrap(e);
    } catch (TimeoutException e) {
      throw new AssertionError("Server session did not complete in time", e);
    } finally {
      executor.shutdownNow();
    }
  }

  private static WireProtocol createServerProtocol(InputStream in, OutputStream out)
      throws Exception {
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    PushbackInputStream input = ProtocolNegotiator.createNegotiationStream(in);
    ProtocolVersion negotiated = negotiator.negotiateAgent(input, out);
    assertEquals(ProtocolVersion.V2, negotiated);
    WireProtocol protocol = new BinaryWireProtocol(input, out);
    assertEquals(ProtocolVersion.V2, protocol.getVersion());
    return protocol;
  }

  private static WireProtocol createClientProtocol(InputStream in, OutputStream out)
      throws Exception {
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    ProtocolVersion negotiated = negotiator.negotiateClient(in, out, ProtocolVersion.V2);
    assertEquals(ProtocolVersion.V2, negotiated);
    WireProtocol protocol = new BinaryWireProtocol(in, out);
    assertEquals(ProtocolVersion.V2, protocol.getVersion());
    return protocol;
  }

  private static Exception unwrap(ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof Exception) {
      return (Exception) cause;
    }
    return new RuntimeException(cause);
  }

  private static final class Duplex {
    final InputStream clientIn;
    final OutputStream clientOut;
    final InputStream serverIn;
    final OutputStream serverOut;

    Duplex() throws Exception {
      PipedInputStream clientInput = new PipedInputStream(32 * 1024);
      PipedInputStream serverInput = new PipedInputStream(32 * 1024);
      this.clientOut = new PipedOutputStream(serverInput);
      this.serverOut = new PipedOutputStream(clientInput);
      this.clientIn = clientInput;
      this.serverIn = serverInput;
    }
  }
}
