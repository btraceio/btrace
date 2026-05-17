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

  static final String ENDPOINT = "https://app.posthog.com/capture/";

  // Hard wall-clock cap for the guard thread (covers DNS + connect + read).
  // connectTimeout alone does not bound DNS resolution — the guard join does.
  static final int GUARD_TIMEOUT_MS = 2000;
  static final int CONNECT_TIMEOUT_MS = 1000;
  static final int READ_TIMEOUT_MS = 1000;

  private static final String PROP_DISABLED = "btrace.telemetry";

  private Telemetry() {}

  static boolean isEnabled() {
    return !"false".equalsIgnoreCase(System.getProperty(PROP_DISABLED, "true"));
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

  // fireAsync returns immediately — the calling thread (agent startup) is never blocked.
  static void fireAsync(final String btraceVersion, final String agentMode) {
    if (!isEnabled()) {
      return;
    }
    final Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                send(btraceVersion, agentMode);
              }
            });
    worker.setDaemon(true);
    worker.setName("btrace-telemetry");

    // Guard caps total wall-clock time including DNS, which connectTimeout
    // alone does not cover. Both threads are daemon threads.
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
    guard.start();
  }

  private static void send(String btraceVersion, String agentMode) {
    try {
      String payload = buildPayload(btraceVersion, agentMode);
      byte[] body = payload.getBytes(Charset.forName("UTF-8"));
      URL url = new URL(ENDPOINT);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
      conn.disconnect();
    } catch (Throwable ignored) {
    }
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
