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
package io.btrace.client;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.tools.attach.AgentLoadException;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.ListFailedExtensionsCommand;
import io.btrace.core.comm.ListProbesCommand;
import io.btrace.core.comm.ReconnectCommand;
import io.btrace.core.comm.StatusCommand;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientTest {

  @TempDir Path tempDir;

  private File createUberJar() throws IOException {
    File uberJar = tempDir.resolve("btrace.jar").toFile();

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(uberJar))) {
      // Add a dummy embedded agent JAR
      JarEntry agentEntry = new JarEntry("META-INF/embedded/btrace-agent.jar");
      jos.putNextEntry(agentEntry);
      jos.write("dummy agent content".getBytes());
      jos.closeEntry();

      // Add a dummy embedded boot JAR
      JarEntry bootEntry = new JarEntry("META-INF/embedded/btrace-boot.jar");
      jos.putNextEntry(bootEntry);
      jos.write("dummy boot content".getBytes());
      jos.closeEntry();

      // Add a marker class to make it a valid JAR
      JarEntry classEntry = new JarEntry("io/btrace/client/Client.class");
      jos.putNextEntry(classEntry);
      jos.write(new byte[0]);
      jos.closeEntry();
    }

    return uberJar;
  }

  @Test
  void testConstructorWithOverrides() {
    String agentJar = "/path/to/agent.jar";

    Client client = new Client(2020, null, ".", false, false, false, false, null, null, agentJar);

    // Use reflection to verify private fields (since they're not exposed)
    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertEquals(agentJar, agentField.get(client));
    } catch (Exception e) {
      fail("Failed to access private fields: " + e.getMessage());
    }
  }

  @Test
  void testConstructorWithNullOverrides() {
    Client client = new Client(2020, null, ".", false, false, false, false, null, null, null);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertNull(agentField.get(client));
    } catch (Exception e) {
      fail("Failed to access private fields: " + e.getMessage());
    }
  }

  @Test
  void testExtractEmbeddedAgentJarNotFound() throws Exception {
    // Create a regular JAR without embedded JARs
    File regularJar = tempDir.resolve("regular.jar").toFile();
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(regularJar))) {
      JarEntry entry = new JarEntry("some/Class.class");
      jos.putNextEntry(entry);
      jos.write(new byte[0]);
      jos.closeEntry();
    }

    // This test would need to mock the Client.class location
    // For now, we just verify the JAR exists
    assertTrue(regularJar.exists());
  }

  @Test
  void testUberJarCreation() throws Exception {
    File uberJar = createUberJar();
    assertTrue(uberJar.exists());

    // Verify embedded JARs exist
    try (JarFile jar = new JarFile(uberJar)) {
      assertNotNull(jar.getJarEntry("META-INF/embedded/btrace-agent.jar"));
      assertNotNull(jar.getJarEntry("META-INF/embedded/btrace-boot.jar"));
    }
  }

  @Test
  void testAgentJarOverrideTakesPrecedence() {
    // When agentJarOverride is set, it should be used instead of discovery
    String overridePath = "/custom/path/btrace-agent.jar";

    Client client =
        new Client(2020, null, ".", false, false, false, false, null, null, overridePath);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertEquals(overridePath, agentField.get(client));
    } catch (Exception e) {
      fail("Failed to verify agentJarOverride: " + e.getMessage());
    }
  }

  @Test
  void testBackwardCompatibility() {
    // Old constructor should still work (no overrides)
    Client client = new Client(2020, null, ".", false, false, false, false, null, null);

    try {
      Field agentField = Client.class.getDeclaredField("agentJarOverride");
      agentField.setAccessible(true);
      assertNull(agentField.get(client));
    } catch (Exception e) {
      fail("Failed to verify backward compatibility: " + e.getMessage());
    }
  }

  @Test
  void preparedDiscoveryAdoptsEndpointAndCredentials() throws Exception {
    Path tokenFile = tempDir.resolve("prepared.token");
    byte[] token = "prepared-secret".getBytes(StandardCharsets.UTF_8);
    Files.write(tokenFile, token);
    Properties properties = new Properties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.address", "::1");
    properties.setProperty("btrace.auth.required", "true");
    properties.setProperty("btrace.auth.tokenFile", tokenFile.toString());
    Client client = new Client(0);

    client.configureConnection(properties);

    assertEquals(43210, readField(client, "port"));
    assertEquals(InetAddress.getByName("::1").getHostAddress(), client.resolveHost("localhost"));
    assertArrayEquals(token, (byte[]) readField(client, "authenticationToken"));
  }

  @Test
  void missingPreparedCredentialsFailClosed() {
    Properties properties = new Properties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.auth.required", "true");

    assertThrows(IOException.class, () -> new Client(0).configureConnection(properties));
  }

  @Test
  void nonLoopbackDiscoveryFailsWithoutReplacingExistingConnection() throws Exception {
    Path tokenFile = tempDir.resolve("prepared.token");
    byte[] token = "prepared-secret".getBytes(StandardCharsets.UTF_8);
    Files.write(tokenFile, token);
    Properties valid = new Properties();
    valid.setProperty("btrace.port", "43210");
    valid.setProperty("btrace.address", "127.0.0.1");
    valid.setProperty("btrace.auth.required", "true");
    valid.setProperty("btrace.auth.tokenFile", tokenFile.toString());
    Client client = new Client(0);
    client.configureConnection(valid);

    Properties invalid = new Properties();
    invalid.setProperty("btrace.port", "12345");
    invalid.setProperty("btrace.address", "192.0.2.1");
    invalid.setProperty("btrace.auth.required", "false");

    assertThrows(IOException.class, () -> client.configureConnection(invalid));
    assertEquals(43210, readField(client, "port"));
    assertEquals("127.0.0.1", client.resolveHost("localhost"));
    assertArrayEquals(token, (byte[]) readField(client, "authenticationToken"));
  }

  @Test
  void preparedAuthenticationResponseIsTimeoutBounded() throws Exception {
    Path tokenFile = tempDir.resolve("prepared.token");
    byte[] token = "prepared-secret".getBytes(StandardCharsets.UTF_8);
    Files.write(tokenFile, token);
    CountDownLatch releaseServer = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    System.setProperty("btrace.protocol.negotiation.timeout", "100");
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<?> accepted =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  byte[] request = new byte[8 + token.length];
                  int offset = 0;
                  while (offset < request.length) {
                    int read =
                        socket.getInputStream().read(request, offset, request.length - offset);
                    if (read < 0) {
                      break;
                    }
                    offset += read;
                  }
                  releaseServer.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      Properties properties = new Properties();
      properties.setProperty("btrace.port", String.valueOf(server.getLocalPort()));
      properties.setProperty("btrace.address", server.getInetAddress().getHostAddress());
      properties.setProperty("btrace.auth.required", "true");
      properties.setProperty("btrace.auth.tokenFile", tokenFile.toString());
      Client client = new Client(0);
      client.configureConnection(properties);

      assertThrows(
          SocketTimeoutException.class,
          () -> client.connectAndListProbes("localhost", command -> {}));
      releaseServer.countDown();
      accepted.get(5, TimeUnit.SECONDS);
      client.close();
    } finally {
      releaseServer.countDown();
      executor.shutdownNow();
      System.clearProperty("btrace.protocol.negotiation.timeout");
    }
  }

  @Test
  void closeClearsDiscoveredCredentials() throws Exception {
    Path tokenFile = tempDir.resolve("prepared.token");
    Files.write(tokenFile, "prepared-secret".getBytes(StandardCharsets.UTF_8));
    Properties properties = new Properties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.auth.required", "true");
    properties.setProperty("btrace.auth.tokenFile", tokenFile.toString());
    Client client = new Client(0);
    client.configureConnection(properties);
    byte[] token = (byte[]) readField(client, "authenticationToken");

    client.close();

    assertNull(readField(client, "authenticationToken"));
    for (byte value : token) {
      assertEquals(0, value);
    }
  }

  @Test
  void recognizesRestrictedDynamicAgentLoadingFailure() {
    AgentLoadException failure =
        new AgentLoadException(
            "Dynamic agent loading is not enabled. Use -XX:+EnableDynamicAgentLoading");

    assertTrue(Client.isDynamicAgentLoadingRestricted(failure));
    assertFalse(
        Client.isDynamicAgentLoadingRestricted(new AgentLoadException("Agent JAR not found")));
    assertFalse(
        Client.isDynamicAgentLoadingRestricted(
            new AgentLoadException(
                "EnableDynamicAgentLoading was enabled but agent initialization failed")));
  }

  @Test
  void restrictedDynamicAgentGuidanceUsesResolvedAgentPathWithoutClaimingFlagState() {
    AgentLoadException cause = new AgentLoadException("Dynamic agent loading is disabled");

    IOException failure =
        Client.dynamicAgentLoadFailure("1234", "/opt/btrace/libs/btrace.jar", cause);

    assertTrue(failure.getMessage().contains("PID 1234"));
    assertTrue(failure.getMessage().contains("-XX:+EnableDynamicAgentLoading"));
    assertTrue(failure.getMessage().contains("-javaagent:/opt/btrace/libs/btrace.jar=port=0"));
    assertTrue(failure.getMessage().contains("did not inspect the target VM flag state"));
    assertSame(cause, failure.getCause());
  }

  /**
   * Fake agent speaking the V1 protocol: consumes {@code expectedCommands} client commands, sends
   * {@code replies}, then reports whether the client closed the connection.
   */
  private static final class FakeAgent implements AutoCloseable {
    final ServerSocket server;
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Future<Boolean> clientClosed;

    FakeAgent(int expectedCommands, Command... replies) throws IOException {
      server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      clientClosed =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  JavaSerializationProtocol protocol =
                      new JavaSerializationProtocol(
                          socket.getInputStream(), socket.getOutputStream());
                  for (int i = 0; i < expectedCommands; i++) {
                    protocol.read();
                  }
                  for (Command reply : replies) {
                    protocol.write(reply);
                  }
                  protocol.flush();
                  socket.setSoTimeout(5000);
                  try {
                    protocol.read();
                    return false;
                  } catch (SocketTimeoutException e) {
                    return false;
                  } catch (IOException e) {
                    return true; // EOF: the client closed the connection
                  }
                }
              });
    }

    Client client() throws IOException {
      Properties properties = new Properties();
      properties.setProperty("btrace.port", String.valueOf(server.getLocalPort()));
      properties.setProperty("btrace.address", "127.0.0.1");
      Client client = new Client(0);
      client.configureConnection(properties);
      return client;
    }

    @Override
    public void close() throws IOException {
      executor.shutdownNow();
      server.close();
    }
  }

  private static void forceV1Protocol() {
    System.setProperty("btrace.comm.protocol", "1");
    System.setProperty("btrace.comm.autoNegotiate", "false");
  }

  private static void clearProtocolProperties() {
    System.clearProperty("btrace.comm.protocol");
    System.clearProperty("btrace.comm.autoNegotiate");
  }

  @Test
  void connectAndListProbesReturnsAfterReplyAndClosesSocket() throws Exception {
    forceV1Protocol();
    try (FakeAgent agent = new FakeAgent(1, new ListProbesCommand())) {
      Client client = agent.client();
      List<Byte> seen = new ArrayList<>();

      client.connectAndListProbes("localhost", cmd -> seen.add(cmd.getType()));

      assertEquals(List.of((byte) Command.LIST_PROBES), seen);
      assertTrue(agent.clientClosed.get(10, TimeUnit.SECONDS), "socket must be closed");
    } finally {
      clearProtocolProperties();
    }
  }

  @Test
  void connectAndListFailedExtensionsReturnsAfterReplyAndClosesSocket() throws Exception {
    forceV1Protocol();
    try (FakeAgent agent = new FakeAgent(1, new ListFailedExtensionsCommand())) {
      Client client = agent.client();
      List<Byte> seen = new ArrayList<>();

      client.connectAndListFailedExtensions("localhost", cmd -> seen.add(cmd.getType()));

      assertEquals(List.of((byte) Command.LIST_FAILED_EXTENSIONS), seen);
      assertTrue(agent.clientClosed.get(10, TimeUnit.SECONDS), "socket must be closed");
    } finally {
      clearProtocolProperties();
    }
  }

  @Test
  void submitThrowsWhenNoAgentIsListening() throws Exception {
    int freePort;
    try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      freePort = probe.getLocalPort();
    }
    Properties properties = new Properties();
    properties.setProperty("btrace.port", String.valueOf(freePort));
    properties.setProperty("btrace.address", "127.0.0.1");
    Client client = new Client(0);
    client.configureConnection(properties);

    IOException failure =
        assertThrows(
            IOException.class,
            () -> client.submit("localhost", null, new byte[0], new String[0], cmd -> {}));
    assertTrue(failure.getMessage().contains(String.valueOf(freePort)));
  }

  @Test
  void submitThrowsWhenStatusReportsFailureAfterForwardingIt() throws Exception {
    forceV1Protocol();
    try (FakeAgent agent = new FakeAgent(2, new StatusCommand(-StatusCommand.STATUS_FLAG))) {
      Client client = agent.client();
      List<Command> seen = new ArrayList<>();
      byte[] code =
          Files.readAllBytes(Paths.get(ClientTest.class.getResource("ClientTest.class").toURI()));

      IOException failure =
          assertThrows(
              IOException.class,
              () -> client.submit("localhost", "probe.class", code, new String[0], seen::add));

      assertTrue(failure.getMessage().contains("probe.class"));
      assertEquals(1, seen.size());
      assertEquals(Command.STATUS, seen.get(0).getType());
      assertFalse(((StatusCommand) seen.get(0)).isSuccess());
      assertTrue(agent.clientClosed.get(10, TimeUnit.SECONDS), "socket must be closed");
    } finally {
      clearProtocolProperties();
    }
  }

  @Test
  void submitClosesConnectionWhenProtocolSetupFails() throws Exception {
    forceV1Protocol();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      executor.submit(
          () -> {
            server.accept().close(); // drop the connection before any protocol handshake
            return null;
          });
      Properties properties = new Properties();
      properties.setProperty("btrace.port", String.valueOf(server.getLocalPort()));
      properties.setProperty("btrace.address", "127.0.0.1");
      Client client = new Client(0);
      client.configureConnection(properties);

      assertThrows(
          IOException.class,
          () -> client.submit("localhost", null, new byte[0], new String[0], cmd -> {}));

      assertNull(readField(client, "sock"));
      assertNull(readField(client, "protocol"));
    } finally {
      executor.shutdownNow();
      clearProtocolProperties();
    }
  }

  @Test
  void interruptedConnectThrowsAndRestoresInterruptFlag() throws Exception {
    int freePort;
    try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      freePort = probe.getLocalPort();
    }
    Properties properties = new Properties();
    properties.setProperty("btrace.port", String.valueOf(freePort));
    properties.setProperty("btrace.address", "127.0.0.1");

    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        Client client = new Client(0);
        client.configureConnection(properties);
        Thread.currentThread().interrupt(); // the retry sleep after the refused connect throws
        if (attempt == 0) {
          assertThrows(
              InterruptedIOException.class,
              () -> client.submit("localhost", null, new byte[0], new String[0], cmd -> {}));
        } else {
          assertThrows(
              InterruptedIOException.class,
              () -> client.connectAndListProbes("localhost", cmd -> {}));
        }
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
        Thread.interrupted(); // clear for the next iteration / other tests
      }
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void reconnectThrowsForUnknownProbe() throws Exception {
    forceV1Protocol();
    try (FakeAgent agent = new FakeAgent(1, new StatusCommand(-ReconnectCommand.STATUS_FLAG))) {
      Client client = agent.client();

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  client.reconnect(
                      "localhost", "no-such-probe", cmd -> {}, new String[] {null, null}));

      assertTrue(failure.getMessage().contains("no-such-probe"));
      assertTrue(agent.clientClosed.get(10, TimeUnit.SECONDS), "socket must be closed");
    } finally {
      clearProtocolProperties();
    }
  }

  @Test
  void connectAndExitProbeStopsTheProbeAndClosesSocket() throws Exception {
    forceV1Protocol();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      // The agent side of a reconnect: acknowledge the probe, then echo the client's EXIT.
      Future<List<Command>> received =
          executor.submit(
              () -> {
                List<Command> commands = new ArrayList<>();
                try (Socket socket = server.accept()) {
                  socket.setSoTimeout(10_000);
                  JavaSerializationProtocol protocol =
                      new JavaSerializationProtocol(
                          socket.getInputStream(), socket.getOutputStream());
                  commands.add(protocol.read());
                  protocol.write(new StatusCommand(ReconnectCommand.STATUS_FLAG));
                  protocol.flush();
                  Command exit = protocol.read();
                  commands.add(exit);
                  protocol.write(exit);
                  protocol.flush();
                  try {
                    protocol.read();
                  } catch (IOException e) {
                    commands.add(null); // EOF: the client closed the connection
                  }
                }
                return commands;
              });
      Properties properties = new Properties();
      properties.setProperty("btrace.port", String.valueOf(server.getLocalPort()));
      properties.setProperty("btrace.address", "127.0.0.1");
      Client client = new Client(0);
      client.configureConnection(properties);

      client.connectAndExitProbe("localhost", "probe-1");

      List<Command> commands = received.get(10, TimeUnit.SECONDS);
      assertEquals(3, commands.size(), "socket must be closed after the EXIT echo");
      assertEquals("probe-1", ((ReconnectCommand) commands.get(0)).getProbeId());
      assertEquals(Command.EXIT, commands.get(1).getType());
      assertEquals(0, ((ExitCommand) commands.get(1)).getExitCode());
    } finally {
      executor.shutdownNow();
      clearProtocolProperties();
    }
  }

  @Test
  void connectAndExitProbeThrowsForUnknownProbe() throws Exception {
    forceV1Protocol();
    try (FakeAgent agent = new FakeAgent(1, new StatusCommand(-ReconnectCommand.STATUS_FLAG))) {
      Client client = agent.client();

      IOException failure =
          assertThrows(
              IOException.class, () -> client.connectAndExitProbe("localhost", "no-such-probe"));

      assertTrue(failure.getMessage().contains("no-such-probe"));
      assertTrue(agent.clientClosed.get(10, TimeUnit.SECONDS), "socket must be closed");
    } finally {
      clearProtocolProperties();
    }
  }

  private static Object readField(Client client, String name) throws Exception {
    Field field = Client.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(client);
  }
}
