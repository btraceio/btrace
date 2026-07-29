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
package tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.client.Client;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.StatusCommand;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** End-to-end coverage for prepared-agent discovery, authentication, and wire negotiation. */
public class PreparedModeAuthenticationFunctionalTest extends RuntimeTest {
  private static final byte[] TOKEN =
      "prepared-mode-functional-secret".getBytes(StandardCharsets.UTF_8);

  @TempDir Path tempDir;

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    setProtocol(2, false, true);
  }

  @AfterEach
  public void restoreProtocol() {
    setProtocol(2, false, true);
  }

  @ParameterizedTest(name = "client v{0}, server v{1}, auto={2}, supplied token={4}")
  @CsvSource({
    "1, 1, false, true, true",
    "2, 2, false, true, true",
    "2, 2, false, true, false",
    "2, 1, true, false, true"
  })
  @DisplayName("Prepared agent authenticates before V1/V2 negotiation and fallback")
  public void preparedAgentAuthenticatesAndNegotiates(
      int clientProtocol,
      int serverProtocol,
      boolean autoNegotiate,
      boolean forceVersion,
      boolean suppliedToken)
      throws Exception {
    Path tokenFile = suppliedToken ? createTokenFile("prepared.token", TOKEN) : null;
    TestApp app = launchPreparedApp(tokenFile, serverProtocol);
    try {
      assertProbeRoundTrip(app.getPid(), clientProtocol, autoNegotiate, forceVersion);
    } finally {
      app.stop();
    }

    if (suppliedToken) {
      assertTrue(Files.isRegularFile(tokenFile), "User-supplied token must survive agent shutdown");
    }
  }

  @Test
  @DisplayName("Prepared agent rejects an incorrect token and accepts a later valid client")
  public void incorrectTokenFailsClosedWithoutStoppingServer() throws Exception {
    Path tokenFile = createTokenFile("incorrect-token.token", TOKEN);
    TestApp app = launchPreparedApp(tokenFile, 2);
    try {
      int pid = app.getPid();
      Files.write(
          tokenFile,
          "incorrect-secret".getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);

      IOException failure = assertAuthenticationFailure(pid);
      assertTrue(
          failure.getMessage().contains("authentication rejected"),
          "Server must reject the incorrect prepared-mode credential");

      Files.write(tokenFile, TOKEN, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      assertProbeRoundTrip(pid, 2, false, true);
    } finally {
      app.stop();
    }
  }

  @Test
  @DisplayName("Prepared client fails closed when the discovered token file disappears")
  public void missingDiscoveredTokenFailsClosedWithoutStoppingServer() throws Exception {
    Path tokenFile = createTokenFile("missing-token.token", TOKEN);
    TestApp app = launchPreparedApp(tokenFile, 2);
    try {
      int pid = app.getPid();
      Files.delete(tokenFile);

      Client client = createClient();
      try {
        assertThrows(
            IOException.class,
            () -> client.attach(String.valueOf(pid), null, getEventsClassPath()));
      } finally {
        client.close();
      }

      createTokenFile(tokenFile.getFileName().toString(), TOKEN);
      assertProbeRoundTrip(pid, 2, false, true);
    } finally {
      app.stop();
    }
  }

  private TestApp launchPreparedApp(Path tokenFile, int protocol) throws Exception {
    String agentArguments = "port=0,noServer=false,telemetry=false";
    if (tokenFile != null) {
      agentArguments += ",authTokenFile=" + tokenFile.toAbsolutePath();
    }
    return launchTestAppWithAgent(
        "resources.Main",
        agentArguments,
        "-Dbtrace.comm.protocol=" + protocol,
        "-Dbtrace.comm.autoNegotiate=false",
        "-Dbtrace.comm.forceVersion=true");
  }

  private IOException assertAuthenticationFailure(int pid) throws Exception {
    Client client = createClient();
    File traceFile = locateTrace("btrace/OnTimerArgTest.java");
    assertNotNull(traceFile, "Test probe source is missing");
    byte[] code = client.compile(traceFile.getAbsolutePath(), getEventsClassPath());
    assertNotNull(code, "BTrace compilation failed");
    try {
      client.attach(String.valueOf(pid), null, getEventsClassPath());
      return assertThrows(
          IOException.class,
          () ->
              client.submit(
                  "localhost",
                  traceFile.getName(),
                  code,
                  new String[] {"timer=200"},
                  command -> {}));
    } finally {
      client.close();
    }
  }

  private void assertProbeRoundTrip(
      int pid, int protocol, boolean autoNegotiate, boolean forceVersion) throws Exception {
    setProtocol(protocol, autoNegotiate, forceVersion);
    File traceFile = locateTrace("btrace/OnTimerArgTest.java");
    assertNotNull(traceFile, "Test probe source is missing");
    Client client = createClient();
    ExecutorService executor = newDaemonExecutor("prepared-mode-probe-submit");
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      client.attach(String.valueOf(pid), null, getEventsClassPath());
      byte[] code = client.compile(traceFile.getAbsolutePath(), getEventsClassPath());
      assertNotNull(code, "BTrace compilation failed");

      Future<?> submission =
          executor.submit(
              () -> {
                try {
                  client.submit(
                      "localhost",
                      traceFile.getName(),
                      code,
                      new String[] {"timer=200"},
                      command -> handleStatus(client, command, started));
                } catch (Throwable failure) {
                  started.completeExceptionally(failure);
                  throw new RuntimeException(failure);
                }
              });

      started.get(20, TimeUnit.SECONDS);
      try {
        submission.get(10, TimeUnit.SECONDS);
      } catch (ExecutionException ignored) {
        // sendDisconnect closes the command stream after the successful STATUS round trip.
      }
    } finally {
      client.close();
      executor.shutdownNow();
    }
  }

  private static void handleStatus(Client client, Command command, CompletableFuture<Void> started)
      throws IOException {
    if (command.getType() != Command.STATUS) {
      return;
    }
    StatusCommand status = (StatusCommand) command;
    if (status.getFlag() != StatusCommand.STATUS_FLAG) {
      return;
    }
    if (!status.isSuccess()) {
      started.completeExceptionally(new IllegalStateException("Probe startup failed"));
      return;
    }
    client.sendDisconnect();
    started.complete(null);
  }

  private Client createClient() {
    File traceFile = locateTrace("btrace/OnTimerArgTest.java");
    assertNotNull(traceFile, "Test probe source is missing");
    return createClientForTests(traceFile.getParentFile().getAbsolutePath());
  }

  private Path createTokenFile(String name, byte[] token) throws IOException {
    Path path = tempDir.resolve(name);
    try {
      Files.createFile(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } catch (UnsupportedOperationException ignored) {
      Files.createFile(path);
    }
    Files.write(path, token, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    return path;
  }

  private static void setProtocol(int protocol, boolean autoNegotiate, boolean forceVersion) {
    System.setProperty("btrace.comm.protocol", String.valueOf(protocol));
    System.setProperty("btrace.comm.autoNegotiate", String.valueOf(autoNegotiate));
    System.setProperty("btrace.comm.forceVersion", String.valueOf(forceVersion));
  }
}
