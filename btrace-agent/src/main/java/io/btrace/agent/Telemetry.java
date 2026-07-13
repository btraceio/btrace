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
package io.btrace.agent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.UUID;

final class Telemetry {

  // Public project API key — safe to commit (PostHog design intent).
  // Replace with the actual key from your PostHog project settings.
  static final String API_KEY = "phc_tGurJ2fAYeouW4k8Txkn3zrfrKgoiuXgrAJP33ufX9Hv";

  static final String ENDPOINT = "https://eu.posthog.com/capture/";

  // The guard requests worker cancellation after this interval. DNS implementations are not
  // required to honor thread interruption, so the daemon worker remains the final startup guard.
  static final int GUARD_TIMEOUT_MS = 2000;
  static final int CONNECT_TIMEOUT_MS = 1000;
  static final int READ_TIMEOUT_MS = 1000;

  private static final String PROP_ENABLED = "btrace.telemetry";

  interface Transport {
    void send(String payload);
  }

  private static final Transport HTTP_TRANSPORT =
      new Transport() {
        @Override
        public void send(String payload) {
          sendHttp(payload);
        }
      };

  private Telemetry() {}

  static boolean isEnabled(String agentValue) {
    if (agentValue != null) {
      return Boolean.parseBoolean(agentValue);
    }
    return Boolean.getBoolean(PROP_ENABLED);
  }

  static String buildPayload(String btraceVersion, String agentMode) {
    String javaVersion = escape(System.getProperty("java.version", "unknown"));
    String osName = escape(System.getProperty("os.name", "unknown"));
    String distinctId = UUID.randomUUID().toString();
    return "{"
        + "\"api_key\":\""
        + API_KEY
        + "\","
        + "\"event\":\"agent_start\","
        + "\"distinct_id\":\""
        + distinctId
        + "\","
        + "\"properties\":{"
        + "\"java_version\":\""
        + javaVersion
        + "\","
        + "\"os_name\":\""
        + osName
        + "\","
        + "\"btrace_version\":\""
        + escape(btraceVersion)
        + "\","
        + "\"agent_mode\":\""
        + escape(agentMode)
        + "\""
        + "}"
        + "}";
  }

  // Consent is checked before creating threads or building the payload.
  static boolean fireAsync(
      String telemetryValue, final String btraceVersion, final String agentMode) {
    return fireAsync(telemetryValue, btraceVersion, agentMode, HTTP_TRANSPORT);
  }

  static boolean fireAsync(
      String telemetryValue,
      final String btraceVersion,
      final String agentMode,
      final Transport transport) {
    if (!isEnabled(telemetryValue)) {
      return false;
    }
    final Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                deliverSafely(transport, buildPayload(btraceVersion, agentMode));
              }
            });
    worker.setDaemon(true);
    worker.setName("btrace-telemetry");

    // The guard requests cancellation if DNS or transport work outlives the configured timeout.
    // Both threads are daemon threads, so telemetry never keeps the target JVM alive.
    Thread guard =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  worker.start();
                  worker.join(GUARD_TIMEOUT_MS);
                  worker.interrupt();
                } catch (Throwable ignored) {
                }
              }
            });
    guard.setDaemon(true);
    guard.setName("btrace-telemetry-guard");
    try {
      guard.start();
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  static void deliverSafely(Transport transport, String payload) {
    try {
      transport.send(payload);
    } catch (Throwable ignored) {
    }
  }

  private static void sendHttp(String payload) {
    HttpURLConnection conn = null;
    try {
      byte[] body = payload.getBytes(Charset.forName("UTF-8"));
      URL url = new URL(ENDPOINT);
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      conn.setRequestProperty("Content-Length", String.valueOf(body.length));
      conn.setDoOutput(true);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(body);
      }
      conn.getResponseCode();
    } catch (Throwable ignored) {
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
