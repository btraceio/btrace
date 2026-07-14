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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionAuthenticatorTest {
  private static final byte[] TOKEN = "prepared-secret".getBytes(StandardCharsets.UTF_8);

  @TempDir Path tempDir;

  @Test
  void authenticatesBeforeV1Commands() throws Exception {
    runAuthenticatedSession(ProtocolVersion.V1);
  }

  @Test
  void authenticatesBeforeV2Commands() throws Exception {
    runAuthenticatedSession(ProtocolVersion.V2);
  }

  @Test
  void incorrectTokenIsRejected() throws Exception {
    Duplex duplex = new Duplex();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> server =
        executor.submit(
            () -> {
              assertThrows(
                  IOException.class,
                  () ->
                      ConnectionAuthenticator.authenticateAgent(
                          duplex.serverIn, duplex.serverOut, TOKEN));
            });
    try {
      assertThrows(
          IOException.class,
          () ->
              ConnectionAuthenticator.authenticateClient(
                  duplex.clientIn,
                  duplex.clientOut,
                  "wrong-secret".getBytes(StandardCharsets.UTF_8)));
      server.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void protocolBytesWithoutCredentialsAreRejected() {
    ByteArrayOutputStream response = new ByteArrayOutputStream();

    assertThrows(
        IOException.class,
        () ->
            ConnectionAuthenticator.authenticateAgent(
                new ByteArrayInputStream(ProtocolVersion.V2.getMagicBytes()), response, TOKEN));

    assertArrayEquals(new byte[] {'B', 'T', 'A', 'F'}, response.toByteArray());
  }

  @Test
  void malformedTokenLengthIsRejected() throws Exception {
    ByteArrayOutputStream request = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(request);
    data.write(new byte[] {'B', 'T', 'A', '1'});
    data.writeInt(ConnectionAuthenticator.MAX_TOKEN_LENGTH + 1);
    ByteArrayOutputStream response = new ByteArrayOutputStream();

    assertThrows(
        IOException.class,
        () ->
            ConnectionAuthenticator.authenticateAgent(
                new ByteArrayInputStream(request.toByteArray()), response, TOKEN));

    assertArrayEquals(new byte[] {'B', 'T', 'A', 'F'}, response.toByteArray());
  }

  @Test
  void tokenFileAllowsOneTrailingNewline() throws Exception {
    Path tokenFile = tempDir.resolve("token");
    Files.write(tokenFile, "prepared-secret\n".getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(TOKEN, ConnectionAuthenticator.readToken(tokenFile));
  }

  @Test
  void tokenFileAllowsOneTrailingWindowsNewline() throws Exception {
    Path tokenFile = tempDir.resolve("windows-token");
    Files.write(tokenFile, "prepared-secret\r\n".getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(TOKEN, ConnectionAuthenticator.readToken(tokenFile));
  }

  @Test
  void tokenFilePreservesSpaces() throws Exception {
    Path tokenFile = tempDir.resolve("spaced-token");
    byte[] token = " prepared secret ".getBytes(StandardCharsets.UTF_8);
    Files.write(tokenFile, token);

    assertArrayEquals(token, ConnectionAuthenticator.readToken(tokenFile));
  }

  @Test
  void tokenFileRejectsMultipleLines() throws Exception {
    Path tokenFile = tempDir.resolve("multi-line-token");
    Files.write(tokenFile, "prepared-secret\nsecond\n".getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> ConnectionAuthenticator.readToken(tokenFile));
  }

  @Test
  void tokenFileRejectsOversizedContentBeforeAllocatingFromItsSize() throws Exception {
    Path tokenFile = tempDir.resolve("oversized-token");
    byte[] content = new byte[ConnectionAuthenticator.MAX_TOKEN_LENGTH + 3];
    Arrays.fill(content, (byte) 'x');
    Files.write(tokenFile, content);

    assertThrows(IOException.class, () -> ConnectionAuthenticator.readToken(tokenFile));
  }

  @Test
  void emptyTokenFileIsRejected() throws Exception {
    Path tokenFile = tempDir.resolve("empty-token");
    Files.write(tokenFile, new byte[0]);

    assertThrows(IOException.class, () -> ConnectionAuthenticator.readToken(tokenFile));
  }

  private static void runAuthenticatedSession(ProtocolVersion version) throws Exception {
    Duplex duplex = new Duplex();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> server =
        executor.submit(
            () -> {
              try {
                ConnectionAuthenticator.authenticateAgent(duplex.serverIn, duplex.serverOut, TOKEN);
                try (WireProtocol protocol =
                    createServerProtocol(version, duplex.serverIn, duplex.serverOut)) {
                  Command command = protocol.read();
                  assertTrue(command instanceof ListProbesCommand);
                  ListProbesCommand response = (ListProbesCommand) command;
                  response.setProbes(Collections.singletonList("probe-1"));
                  protocol.write(response);
                  protocol.flush();
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    try {
      ConnectionAuthenticator.authenticateClient(duplex.clientIn, duplex.clientOut, TOKEN);
      try (WireProtocol protocol =
          createClientProtocol(version, duplex.clientIn, duplex.clientOut)) {
        protocol.write(new ListProbesCommand());
        protocol.flush();
        Command response = protocol.read();
        assertTrue(response instanceof ListProbesCommand);
        assertEquals(
            Collections.singletonList("probe-1"), ((ListProbesCommand) response).getProbes());
      }
      server.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw e;
    } finally {
      executor.shutdownNow();
    }
  }

  private static WireProtocol createServerProtocol(
      ProtocolVersion version, InputStream input, OutputStream output) throws Exception {
    if (version == ProtocolVersion.V1) {
      return new JavaSerializationProtocol(input, output);
    }
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    PushbackInputStream negotiationInput = ProtocolNegotiator.createNegotiationStream(input);
    assertEquals(ProtocolVersion.V2, negotiator.negotiateAgent(negotiationInput, output));
    return new BinaryWireProtocol(negotiationInput, output);
  }

  private static WireProtocol createClientProtocol(
      ProtocolVersion version, InputStream input, OutputStream output) throws Exception {
    if (version == ProtocolVersion.V1) {
      return new JavaSerializationProtocol(input, output);
    }
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    assertEquals(ProtocolVersion.V2, negotiator.negotiateClient(input, output, ProtocolVersion.V2));
    return new BinaryWireProtocol(input, output);
  }

  private static final class Duplex {
    final InputStream clientIn;
    final OutputStream clientOut;
    final InputStream serverIn;
    final OutputStream serverOut;

    Duplex() throws IOException {
      PipedInputStream clientInput = new PipedInputStream(32 * 1024);
      PipedInputStream serverInput = new PipedInputStream(32 * 1024);
      clientOut = new PipedOutputStream(serverInput);
      serverOut = new PipedOutputStream(clientInput);
      clientIn = clientInput;
      serverIn = serverInput;
    }
  }
}
