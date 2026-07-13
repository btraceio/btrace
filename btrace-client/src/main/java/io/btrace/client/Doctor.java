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

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import io.btrace.core.comm.ConnectionAuthenticator;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Read-only target readiness diagnostics for {@code btrace doctor}. */
final class Doctor {
  static final int EXIT_READY = 0;
  static final int EXIT_PREPARATION_REQUIRED = 2;
  static final int EXIT_INACCESSIBLE = 3;
  static final int EXIT_UNEXPECTED_FAILURE = 4;

  private static final String DYNAMIC_LOADING_OBSERVATION = "not_tested";
  private static final String DYNAMIC_REMEDIATION =
      "Relaunch with -XX:+EnableDynamicAgentLoading before using dynamic attach.";
  private static final String PREPARED_REMEDIATION =
      "Relaunch with -javaagent:/path/to/btrace.jar=port=0 for reviewed prepared mode.";

  private Doctor() {}

  static int run(String[] args, PrintWriter output, PrintWriter error) {
    return run(args, output, error, new VirtualMachineAttacher());
  }

  static int run(String[] args, PrintWriter output, PrintWriter error, Attacher attacher) {
    ParsedArguments parsed = ParsedArguments.parse(args);
    if (parsed.error != null) {
      if (parsed.json) {
        Report report = Report.unexpected(parsed.pid, attacher.isAvailable(), parsed.error);
        output.println(report.toJson());
      } else {
        error.println(parsed.error);
        error.println("Usage: btrace doctor <pid> [--json]");
      }
      output.flush();
      error.flush();
      return EXIT_UNEXPECTED_FAILURE;
    }

    Report report = inspect(parsed.pid, attacher);
    output.println(parsed.json ? report.toJson() : report.toHuman());
    output.flush();
    return report.status.exitCode;
  }

  static Report inspect(String pid, Attacher attacher) {
    boolean attachApiAvailable = attacher.isAvailable();
    if (!attachApiAvailable) {
      return Report.inaccessible(pid, false, "Attach API is unavailable in this BTrace runtime.");
    }

    try (Target target = attacher.attach(pid)) {
      Properties properties = target.getSystemProperties();
      return inspectProperties(pid, properties);
    } catch (AttachNotSupportedException | IOException | SecurityException inaccessible) {
      return Report.inaccessible(pid, true, safeMessage(inaccessible));
    } catch (Exception | LinkageError unexpected) {
      return Report.unexpected(pid, true, safeMessage(unexpected));
    }
  }

  private static Report inspectProperties(String pid, Properties properties) {
    String javaVersion = safeText(properties.getProperty("java.version"));
    String javaVendor =
        safeText(properties.getProperty("java.vendor", properties.getProperty("java.vm.vendor")));
    String portValue = trimToNull(properties.getProperty("btrace.port"));
    if (portValue == null) {
      return Report.preparationRequired(
          pid,
          javaVersion,
          javaVendor,
          false,
          null,
          null,
          null,
          null,
          "No BTrace control endpoint is published.");
    }

    int port;
    try {
      port = Integer.parseInt(portValue);
    } catch (NumberFormatException invalid) {
      return Report.preparationRequired(
          pid,
          javaVersion,
          javaVendor,
          true,
          null,
          null,
          null,
          null,
          "The published BTrace port is invalid.");
    }
    if (port < 1 || port > 65535) {
      return Report.preparationRequired(
          pid,
          javaVersion,
          javaVendor,
          true,
          null,
          null,
          null,
          null,
          "The published BTrace port is invalid.");
    }

    String address = trimToNull(properties.getProperty("btrace.address"));
    if (address != null && !isNumericLoopback(address)) {
      return Report.preparationRequired(
          pid,
          javaVersion,
          javaVendor,
          true,
          address,
          port,
          null,
          null,
          "The published BTrace address is not loopback.");
    }

    String authenticationValue = trimToNull(properties.getProperty("btrace.auth.required"));
    if (authenticationValue != null
        && !"true".equalsIgnoreCase(authenticationValue)
        && !"false".equalsIgnoreCase(authenticationValue)) {
      return Report.preparationRequired(
          pid,
          javaVersion,
          javaVendor,
          true,
          address,
          port,
          null,
          null,
          "The BTrace authentication metadata is invalid.");
    }

    boolean authenticationRequired = Boolean.parseBoolean(authenticationValue);
    String tokenFile = trimToNull(properties.getProperty("btrace.auth.tokenFile"));
    Boolean credentialReadable = null;
    if (authenticationRequired) {
      credentialReadable = isUsableCredentialFile(tokenFile);
      if (!credentialReadable) {
        return Report.preparationRequired(
            pid,
            javaVersion,
            javaVendor,
            true,
            address,
            port,
            true,
            false,
            "The prepared-mode credential file is missing or unreadable.");
      }
    }

    return Report.ready(
        pid, javaVersion, javaVendor, address, port, authenticationRequired, credentialReadable);
  }

  private static boolean isNumericLoopback(String address) {
    if (!address.matches("[0-9a-fA-F:.%]+")) {
      return false;
    }
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (IOException invalid) {
      return false;
    }
  }

  private static boolean isUsableCredentialFile(String tokenFile) {
    if (tokenFile == null) {
      return false;
    }
    try {
      Path path = Paths.get(tokenFile).toAbsolutePath().normalize();
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
        return false;
      }
      byte[] token = ConnectionAuthenticator.readToken(path);
      Arrays.fill(token, (byte) 0);
      return true;
    } catch (IOException | RuntimeException invalid) {
      return false;
    }
  }

  private static String safeMessage(Throwable failure) {
    String message = safeText(failure.getMessage());
    if (message == null) {
      return failure.getClass().getSimpleName();
    }
    return message;
  }

  private static String safeText(String value) {
    String text = trimToNull(value);
    if (text == null) {
      return null;
    }
    StringBuilder safe = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char character = text.charAt(i);
      safe.append(Character.isISOControl(character) ? ' ' : character);
    }
    return safe.toString();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  interface Attacher {
    boolean isAvailable();

    Target attach(String pid) throws Exception;
  }

  interface Target extends AutoCloseable {
    Properties getSystemProperties() throws IOException;

    @Override
    void close() throws IOException;
  }

  enum Status {
    READY("ready", EXIT_READY),
    PREPARATION_REQUIRED("preparation_required", EXIT_PREPARATION_REQUIRED),
    INACCESSIBLE("inaccessible", EXIT_INACCESSIBLE),
    UNEXPECTED_FAILURE("unexpected_failure", EXIT_UNEXPECTED_FAILURE);

    final String jsonName;
    final int exitCode;

    Status(String jsonName, int exitCode) {
      this.jsonName = jsonName;
      this.exitCode = exitCode;
    }
  }

  static final class Report {
    final String pid;
    final Status status;
    final boolean attachApiAvailable;
    final boolean targetAccessible;
    final String javaVersion;
    final String javaVendor;
    final boolean endpointPublished;
    final boolean endpointReady;
    final String address;
    final Integer port;
    final Boolean authenticationRequired;
    final Boolean credentialReadable;
    final String detail;
    final List<String> remediation;

    private Report(
        String pid,
        Status status,
        boolean attachApiAvailable,
        boolean targetAccessible,
        String javaVersion,
        String javaVendor,
        boolean endpointPublished,
        boolean endpointReady,
        String address,
        Integer port,
        Boolean authenticationRequired,
        Boolean credentialReadable,
        String detail,
        List<String> remediation) {
      this.pid = pid;
      this.status = status;
      this.attachApiAvailable = attachApiAvailable;
      this.targetAccessible = targetAccessible;
      this.javaVersion = javaVersion;
      this.javaVendor = javaVendor;
      this.endpointPublished = endpointPublished;
      this.endpointReady = endpointReady;
      this.address = address;
      this.port = port;
      this.authenticationRequired = authenticationRequired;
      this.credentialReadable = credentialReadable;
      this.detail = detail;
      this.remediation = remediation;
    }

    static Report ready(
        String pid,
        String javaVersion,
        String javaVendor,
        String address,
        int port,
        boolean authenticationRequired,
        Boolean credentialReadable) {
      return new Report(
          pid,
          Status.READY,
          true,
          true,
          javaVersion,
          javaVendor,
          true,
          true,
          address,
          port,
          authenticationRequired,
          credentialReadable,
          "A BTrace control endpoint is ready.",
          Collections.emptyList());
    }

    static Report preparationRequired(
        String pid,
        String javaVersion,
        String javaVendor,
        boolean endpointPublished,
        String address,
        Integer port,
        Boolean authenticationRequired,
        Boolean credentialReadable,
        String detail) {
      List<String> remediation = new ArrayList<>();
      remediation.add(DYNAMIC_REMEDIATION);
      remediation.add(PREPARED_REMEDIATION);
      return new Report(
          pid,
          Status.PREPARATION_REQUIRED,
          true,
          true,
          javaVersion,
          javaVendor,
          endpointPublished,
          false,
          address,
          port,
          authenticationRequired,
          credentialReadable,
          detail,
          remediation);
    }

    static Report inaccessible(String pid, boolean attachApiAvailable, String detail) {
      List<String> remediation = new ArrayList<>();
      remediation.add("Run as an OS user permitted to attach to the target JVM.");
      remediation.add(PREPARED_REMEDIATION);
      return new Report(
          pid,
          Status.INACCESSIBLE,
          attachApiAvailable,
          false,
          null,
          null,
          false,
          false,
          null,
          null,
          null,
          null,
          detail,
          remediation);
    }

    static Report unexpected(String pid, boolean attachApiAvailable, String detail) {
      return new Report(
          pid,
          Status.UNEXPECTED_FAILURE,
          attachApiAvailable,
          false,
          null,
          null,
          false,
          false,
          null,
          null,
          null,
          null,
          detail,
          Collections.singletonList("Rerun with -v and report the unexpected failure."));
    }

    String toHuman() {
      StringBuilder text = new StringBuilder();
      text.append("BTrace doctor for PID ").append(pid).append('\n');
      text.append("Status: ")
          .append(status.name())
          .append(" (exit ")
          .append(status.exitCode)
          .append(")\n");
      text.append("Target JDK: ")
          .append(javaVersion != null ? javaVersion : "unknown")
          .append(" (")
          .append(javaVendor != null ? javaVendor : "unknown vendor")
          .append(")\n");
      text.append("Attach API: ")
          .append(attachApiAvailable ? "available" : "unavailable")
          .append('\n');
      text.append("Target attach: ")
          .append(targetAccessible ? "accessible" : "inaccessible")
          .append('\n');
      text.append("Dynamic agent loading: not tested (doctor never loads an agent)\n");
      if (endpointReady) {
        text.append("BTrace endpoint: ready at ")
            .append(address != null ? address : "localhost")
            .append(':')
            .append(port)
            .append('\n');
        text.append("Authentication: ")
            .append(Boolean.TRUE.equals(authenticationRequired) ? "required" : "not required");
        if (credentialReadable != null) {
          text.append("; credential file ").append(credentialReadable ? "readable" : "unreadable");
        }
        text.append('\n');
      } else {
        text.append("BTrace endpoint: not ready\n");
      }
      text.append("Detail: ").append(detail).append('\n');
      text.append("Observation: doctor did not inspect the target VM flag state.\n");
      if (!remediation.isEmpty()) {
        text.append("Remediation:\n");
        for (String action : remediation) {
          text.append("- ").append(action).append('\n');
        }
      }
      if (targetAccessible) {
        text.append("Actions: read target system properties and detached; ");
      } else {
        text.append("Actions: target properties were not available; ");
      }
      text.append("no agent load or BTrace command connection was attempted.");
      return text.toString();
    }

    String toJson() {
      StringBuilder json = new StringBuilder();
      json.append('{');
      field(json, "schemaVersion", 1).append(',');
      field(json, "pid", pid).append(',');
      field(json, "status", status.jsonName).append(',');
      field(json, "exitCode", status.exitCode).append(',');
      json.append("\"target\":{");
      field(json, "jdkVersion", javaVersion).append(',');
      field(json, "vendor", javaVendor).append("},");
      json.append("\"attach\":{");
      field(json, "apiAvailable", attachApiAvailable).append(',');
      field(json, "targetAccessible", targetAccessible).append(',');
      field(json, "agentLoadingPermission", DYNAMIC_LOADING_OBSERVATION).append("},");
      json.append("\"operation\":{");
      field(json, "readOnly", true).append(',');
      field(json, "agentLoadAttempted", false).append(',');
      field(json, "commandConnectionOpened", false).append("},");
      json.append("\"btrace\":{");
      field(json, "endpointPublished", endpointPublished).append(',');
      field(json, "ready", endpointReady).append(',');
      field(json, "address", address).append(',');
      field(json, "port", port).append(',');
      field(json, "authenticationRequired", authenticationRequired).append(',');
      field(json, "credentialReadable", credentialReadable).append("},");
      field(json, "detail", detail).append(',');
      json.append("\"remediation\":[");
      for (int i = 0; i < remediation.size(); i++) {
        if (i > 0) {
          json.append(',');
        }
        string(json, remediation.get(i));
      }
      return json.append("]}").toString();
    }

    private static StringBuilder field(StringBuilder json, String name, Object value) {
      string(json, name).append(':');
      if (value == null) {
        return json.append("null");
      }
      if (value instanceof Boolean || value instanceof Number) {
        return json.append(value);
      }
      return string(json, String.valueOf(value));
    }

    private static StringBuilder string(StringBuilder json, String value) {
      json.append('"');
      for (int i = 0; i < value.length(); i++) {
        char character = value.charAt(i);
        switch (character) {
          case '"':
            json.append("\\\"");
            break;
          case '\\':
            json.append("\\\\");
            break;
          case '\b':
            json.append("\\b");
            break;
          case '\f':
            json.append("\\f");
            break;
          case '\n':
            json.append("\\n");
            break;
          case '\r':
            json.append("\\r");
            break;
          case '\t':
            json.append("\\t");
            break;
          default:
            if (character < 0x20) {
              json.append(String.format("\\u%04x", (int) character));
            } else {
              json.append(character);
            }
        }
      }
      return json.append('"');
    }
  }

  private static final class ParsedArguments {
    final String pid;
    final boolean json;
    final String error;

    private ParsedArguments(String pid, boolean json, String error) {
      this.pid = pid;
      this.json = json;
      this.error = error;
    }

    static ParsedArguments parse(String[] args) {
      boolean json = false;
      String pid = null;
      for (String argument : args) {
        if ("--json".equals(argument)) {
          if (json) {
            return new ParsedArguments(pid, true, "--json may be specified only once.");
          }
          json = true;
        } else if (pid == null) {
          pid = argument;
        } else {
          return new ParsedArguments(pid, json, "doctor accepts exactly one PID.");
        }
      }
      if (pid == null || !pid.matches("[1-9][0-9]*")) {
        return new ParsedArguments(pid, json, "doctor requires a positive numeric PID.");
      }
      return new ParsedArguments(pid, json, null);
    }
  }

  private static final class VirtualMachineAttacher implements Attacher {
    @Override
    public boolean isAvailable() {
      try {
        Class.forName("com.sun.tools.attach.VirtualMachine", false, Doctor.class.getClassLoader());
        return true;
      } catch (ClassNotFoundException | LinkageError unavailable) {
        return false;
      }
    }

    @Override
    public Target attach(String pid) throws Exception {
      VirtualMachine machine = VirtualMachine.attach(pid);
      return new Target() {
        @Override
        public Properties getSystemProperties() throws IOException {
          return machine.getSystemProperties();
        }

        @Override
        public void close() throws IOException {
          machine.detach();
        }
      };
    }
  }
}
