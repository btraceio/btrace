/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tests.harness.Completion;

/**
 * End-to-end coverage for the bundled {@code btrace-otel} extension: a script attached by the real
 * client records spans and counters in the target JVM, and the extension posts them as OTLP/HTTP
 * JSON to a collector stub running in this test JVM.
 */
public class OTelExtensionIntegrationTest extends RuntimeTest {
  private static final Pattern SPAN_NAME = Pattern.compile("\"name\":\"Main\\.callB\"");
  private static final Pattern CALLS =
      Pattern.compile(
          "\\{\"name\":\"main\\.callB\\.calls\",\"sum\":\\{\"dataPoints\":\\[\\{[^}]*\"asInt\":\"(\\d+)\"\\}\\],"
              + "\"aggregationTemporality\":1,\"isMonotonic\":true\\}");

  private final List<String> traces = new CopyOnWriteArrayList<>();
  private final List<String> metrics = new CopyOnWriteArrayList<>();
  private HttpServer collector;

  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    try (ServerSocket ss = new ServerSocket(0)) {
      btracePort = ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find a free port", e);
    }
  }

  @BeforeEach
  public void startCollector() throws IOException {
    collector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    collector.createContext("/v1/traces", ex -> receive(ex, traces));
    collector.createContext("/v1/metrics", ex -> receive(ex, metrics));
    collector.start();
  }

  @AfterEach
  public void stopCollector() {
    collector.stop(0);
  }

  @Test
  public void exportsSpansAndDeltaCountersToCollector() throws Exception {
    attachDelayMs = 500;
    String endpoint = "http://127.0.0.1:" + collector.getAddress().getPort();
    testDynamic(
        "resources.Main",
        "btrace/OTelExportTest.java",
        new String[] {
          "otel.endpoint=" + endpoint, "otel.service.name=otel-it", "otel.flush.interval.ms=200"
        },
        Completion.untilContains("OTEL: recorded"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed: " + stdout);
            assertFalse(stdout.contains("btrace-otel: export failed"), stdout);
            assertTrue(stdout.contains("OTEL: recorded"), stdout);
          }
        });

    // The script exits after its third call; closing the extension performs the final export,
    // which reaches the collector asynchronously from the target JVM.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    int spans = 0;
    long counted = 0;
    while (System.nanoTime() < deadline) {
      spans = countSpans();
      counted = sumCalls();
      if (spans >= 3 && counted == spans) {
        break;
      }
      Thread.sleep(100);
    }
    assertTrue(spans >= 3, "expected at least 3 exported spans, got " + spans + ": " + traces);
    // Every probe hit records one span and one counter increment; DELTA export sums must add up
    assertEquals(spans, counted, "counter deltas: " + metrics);
    String trace = traces.get(0);
    assertTrue(trace.contains("{\"stringValue\":\"otel-it\"}"), trace);
    assertTrue(
        trace.contains(
            "\"attributes\":[{\"key\":\"probe\",\"value\":{\"stringValue\":\"OTelExportTest\"}}]"),
        trace);
    // Caller-supplied ids correlate the span with an existing trace
    assertTrue(
        Pattern.compile(
                "\\{\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\",\"spanId\":\"[0-9a-f]{16}\","
                    + "\"parentSpanId\":\"00f067aa0ba902b7\",\"name\":\"Main\\.callB\\.linked\"")
            .matcher(String.join("\n", traces))
            .find(),
        "linked span not exported: " + traces);
  }

  private int countSpans() {
    int count = 0;
    for (String json : traces) {
      Matcher m = SPAN_NAME.matcher(json);
      while (m.find()) {
        count++;
      }
    }
    return count;
  }

  private long sumCalls() {
    long sum = 0;
    for (String json : metrics) {
      Matcher m = CALLS.matcher(json);
      while (m.find()) {
        sum += Long.parseLong(m.group(1));
      }
    }
    return sum;
  }

  private static void receive(HttpExchange ex, List<String> sink) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (InputStream in = ex.getRequestBody()) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) {
        bos.write(buf, 0, n);
      }
    }
    sink.add(new String(bos.toByteArray(), StandardCharsets.UTF_8));
    ex.sendResponseHeaders(200, -1);
    ex.close();
  }
}
