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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorTest {
  @TempDir Path tempDir;

  @Test
  void humanReadyReportUsesOnlyReadOnlyAttachOperations() throws Exception {
    Path token = tempDir.resolve("prepared.token");
    Files.write(token, "secret".getBytes(StandardCharsets.UTF_8));
    Properties properties = baseProperties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.address", "127.0.0.1");
    properties.setProperty("btrace.auth.required", "true");
    properties.setProperty("btrace.auth.tokenFile", token.toString());
    RecordingAttacher attacher = new RecordingAttacher(properties);
    StringWriter output = new StringWriter();
    StringWriter error = new StringWriter();

    int exitCode =
        Doctor.run(
            new String[] {"1234"}, new PrintWriter(output), new PrintWriter(error), attacher);

    assertEquals(Doctor.EXIT_READY, exitCode);
    assertTrue(output.toString().contains("Status: READY (exit 0)"));
    assertTrue(output.toString().contains("Target JDK: 21.0.2 (Eclipse Adoptium)"));
    assertTrue(output.toString().contains("Dynamic agent loading: not tested"));
    assertTrue(output.toString().contains("BTrace endpoint: ready at 127.0.0.1:43210"));
    assertTrue(output.toString().contains("Authentication: required; credential file readable"));
    assertTrue(output.toString().contains("no agent load or BTrace command connection"));
    assertEquals("", error.toString());
    assertEquals(1, attacher.attachCount);
    assertEquals(1, attacher.propertiesReadCount);
    assertEquals(1, attacher.closeCount);
  }

  @Test
  void jsonPreparationReportHasStableFactsAndExitCode() {
    RecordingAttacher attacher = new RecordingAttacher(baseProperties());
    StringWriter output = new StringWriter();

    int exitCode =
        Doctor.run(
            new String[] {"--json", "1234"},
            new PrintWriter(output),
            new PrintWriter(new StringWriter()),
            attacher);

    assertEquals(Doctor.EXIT_PREPARATION_REQUIRED, exitCode);
    String json = output.toString().trim();
    assertTrue(json.startsWith("{\"schemaVersion\":1,"));
    assertTrue(json.contains("\"status\":\"preparation_required\""));
    assertTrue(json.contains("\"exitCode\":2"));
    assertTrue(json.contains("\"jdkVersion\":\"21.0.2\""));
    assertTrue(json.contains("\"apiAvailable\":true"));
    assertTrue(json.contains("\"targetAccessible\":true"));
    assertTrue(json.contains("\"agentLoadingPermission\":\"not_tested\""));
    assertTrue(json.contains("\"readOnly\":true"));
    assertTrue(json.contains("\"agentLoadAttempted\":false"));
    assertTrue(json.contains("\"commandConnectionOpened\":false"));
    assertTrue(json.contains("\"endpointPublished\":false"));
    assertTrue(json.contains("-XX:+EnableDynamicAgentLoading"));
    assertTrue(json.contains("-javaagent:/path/to/btrace.jar=port=0"));
    assertTrue(json.endsWith("}"));
  }

  @Test
  void unreadablePreparedCredentialRequiresPreparation() {
    Properties properties = baseProperties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.address", "::1");
    properties.setProperty("btrace.auth.required", "true");
    properties.setProperty("btrace.auth.tokenFile", tempDir.resolve("missing").toString());

    Doctor.Report report = Doctor.inspect("1234", new RecordingAttacher(properties));

    assertEquals(Doctor.Status.PREPARATION_REQUIRED, report.status);
    assertTrue(report.endpointPublished);
    assertFalse(report.endpointReady);
    assertEquals(Boolean.TRUE, report.authenticationRequired);
    assertEquals(Boolean.FALSE, report.credentialReadable);
  }

  @Test
  void emptyPreparedCredentialRequiresPreparation() throws Exception {
    Path token = tempDir.resolve("empty.token");
    Files.write(token, new byte[0]);
    Properties properties = baseProperties();
    properties.setProperty("btrace.port", "43210");
    properties.setProperty("btrace.address", "127.0.0.1");
    properties.setProperty("btrace.auth.required", "true");
    properties.setProperty("btrace.auth.tokenFile", token.toString());

    Doctor.Report report = Doctor.inspect("1234", new RecordingAttacher(properties));

    assertEquals(Doctor.Status.PREPARATION_REQUIRED, report.status);
    assertEquals(Boolean.FALSE, report.credentialReadable);
  }

  @Test
  void attachRejectionIsInaccessible() {
    Doctor.Attacher attacher = new FailingAttacher(new IOException("permission denied"));
    StringWriter output = new StringWriter();

    int exitCode =
        Doctor.run(
            new String[] {"1234"},
            new PrintWriter(output),
            new PrintWriter(new StringWriter()),
            attacher);

    assertEquals(Doctor.EXIT_INACCESSIBLE, exitCode);
    assertTrue(output.toString().contains("Status: INACCESSIBLE (exit 3)"));
    assertTrue(output.toString().contains("Target attach: inaccessible"));
    assertTrue(output.toString().contains("permission denied"));
  }

  @Test
  void unavailableAttachApiDoesNotTryTarget() {
    RecordingAttacher attacher = new RecordingAttacher(baseProperties());
    attacher.available = false;

    Doctor.Report report = Doctor.inspect("1234", attacher);

    assertEquals(Doctor.Status.INACCESSIBLE, report.status);
    assertFalse(report.attachApiAvailable);
    assertEquals(0, attacher.attachCount);
  }

  @Test
  void unexpectedAttachFailureHasDistinctExitCode() {
    Doctor.Attacher attacher = new FailingAttacher(new IllegalStateException("broken provider"));

    Doctor.Report report = Doctor.inspect("1234", attacher);

    assertEquals(Doctor.Status.UNEXPECTED_FAILURE, report.status);
    assertEquals(Doctor.EXIT_UNEXPECTED_FAILURE, report.status.exitCode);
  }

  @Test
  void invalidPidReturnsJsonUsageFailureWithoutAttaching() {
    RecordingAttacher attacher = new RecordingAttacher(baseProperties());
    StringWriter output = new StringWriter();

    int exitCode =
        Doctor.run(
            new String[] {"not-a-pid", "--json"},
            new PrintWriter(output),
            new PrintWriter(new StringWriter()),
            attacher);

    assertEquals(Doctor.EXIT_UNEXPECTED_FAILURE, exitCode);
    assertTrue(output.toString().contains("\"status\":\"unexpected_failure\""));
    assertEquals(0, attacher.attachCount);
  }

  private static Properties baseProperties() {
    Properties properties = new Properties();
    properties.setProperty("java.version", "21.0.2");
    properties.setProperty("java.vendor", "Eclipse Adoptium");
    return properties;
  }

  private static final class RecordingAttacher implements Doctor.Attacher {
    private final Properties properties;
    boolean available = true;
    int attachCount;
    int propertiesReadCount;
    int closeCount;

    RecordingAttacher(Properties properties) {
      this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public Doctor.Target attach(String pid) {
      attachCount++;
      return new Doctor.Target() {
        @Override
        public Properties getSystemProperties() {
          propertiesReadCount++;
          return properties;
        }

        @Override
        public void close() {
          closeCount++;
        }
      };
    }
  }

  private static final class FailingAttacher implements Doctor.Attacher {
    private final Exception failure;

    FailingAttacher(Exception failure) {
      this.failure = failure;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Doctor.Target attach(String pid) throws Exception {
      throw failure;
    }
  }
}
