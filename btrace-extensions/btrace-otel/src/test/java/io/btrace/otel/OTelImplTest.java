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
package io.btrace.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OTelImplTest {
  private static final Pattern SPAN =
      Pattern.compile(
          "\\{\"traceId\":\"([0-9a-f]{32})\",\"spanId\":\"([0-9a-f]{16})\""
              + "(?:,\"parentSpanId\":\"([0-9a-f]{16})\")?,\"name\":\"((?:[^\"\\\\]|\\\\.)*)\""
              + ",\"kind\":1,\"startTimeUnixNano\":\"(\\d+)\",\"endTimeUnixNano\":\"(\\d+)\"");

  private HttpServer server;
  private final BlockingQueue<String> traces = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> metrics = new LinkedBlockingQueue<>();
  private OTelImpl otel;

  @BeforeEach
  void startCollector() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/v1/traces", ex -> receive(ex.getRequestBody(), traces, ex));
    server.createContext("/v1/metrics", ex -> receive(ex.getRequestBody(), metrics, ex));
    server.start();
  }

  @AfterEach
  void stop() {
    if (otel != null) {
      otel.close();
    }
    server.stop(0);
  }

  @Test
  void exportsSpanWithRecordTimeAndEscapedAttribute() throws Exception {
    otel = start(context("otel.flush.interval.ms=500", "otel.service.name=svc"));

    otel.span("op\"1", "key", "v\u0001\\", 1_000_000L);
    long recordedAt = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());

    String json = traces.poll(5, TimeUnit.SECONDS);
    assertNotNull(json, "no trace export received");
    assertTrue(
        json.contains("{\"key\":\"service.name\",\"value\":{\"stringValue\":\"svc\"}}"), json);
    assertTrue(
        json.contains(
            "\"attributes\":[{\"key\":\"key\",\"value\":{\"stringValue\":\"v\\u0001\\\\\"}}]"),
        json);
    Matcher m = SPAN.matcher(json);
    assertTrue(m.find(), json);
    assertNull(m.group(3), "no parent for a plain span");
    assertEquals("op\\\"1", m.group(4));
    long start = Long.parseLong(m.group(5));
    long end = Long.parseLong(m.group(6));
    assertEquals(1_000_000L, end - start);
    // The end time is taken when the span is recorded, not when the batch is exported 500ms later
    assertTrue(
        Math.abs(recordedAt - end) < TimeUnit.MILLISECONDS.toNanos(200),
        "end=" + end + " recordedAt=" + recordedAt);
    assertFalse(m.find(), "expected a single span: " + json);
  }

  @Test
  void usesCallerSuppliedTraceAndParentIds() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000"));
    String traceId = "4BF92F3577B34DA6A3CE929D0E0E4736";
    String parentId = "00f067aa0ba902b7";

    otel.spanInTrace(traceId, parentId, "linked", 10);
    otel.spanInTrace(traceId, null, "root-in-trace", 10);
    otel.close();

    String json = traces.poll(5, TimeUnit.SECONDS);
    assertNotNull(json);
    Matcher m = SPAN.matcher(json);
    assertTrue(m.find(), json);
    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", m.group(1));
    assertEquals(parentId, m.group(3));
    assertEquals("linked", m.group(4));
    assertTrue(m.find(), json);
    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", m.group(1));
    assertNull(m.group(3), json);
    assertEquals("root-in-trace", m.group(4));
  }

  @Test
  void replacesMalformedCallerIds() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000"));
    String[][] ids = {
      {"00000000000000000000000000000000", "0000000000000000"}, // all zeros are invalid
      {"4bf92f3577b34da6a3ce929d0e0e47", "00f067aa0ba902"}, // too short
      {"4bf92f3577b34da6a3ce929d0e0e473g", "00f067aa0ba902bz"}, // not hex
      // Non-ASCII digits that Character.digit would accept
      {"\uff14bf92f3577b34da6a3ce929d0e0e4736", "\u0660\u0660f067aa0ba902b7"},
    };
    for (String[] pair : ids) {
      otel.spanInTrace(pair[0], pair[1], "bad", 1);
    }
    otel.close();

    String json = traces.poll(5, TimeUnit.SECONDS);
    assertNotNull(json);
    Matcher m = SPAN.matcher(json);
    int count = 0;
    while (m.find()) {
      count++;
      assertFalse(m.group(1).equals("00000000000000000000000000000000"), json);
      assertNull(m.group(3), "malformed parent must be dropped: " + json);
    }
    assertEquals(ids.length, count, json);
    assertFalse(json.contains("4bf92f3577b34da6a3ce929d0e0e47"), json);
  }

  @Test
  void nestsSpansThroughCallerAllocatedIds() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000"));
    String traceId = otel.newTraceId();
    String parentId = otel.newSpanId();
    assertTrue(traceId.matches("[0-9a-f]{32}"), traceId);
    assertTrue(parentId.matches("[0-9a-f]{16}"), parentId);

    // Children end (and are recorded) before their parent, as with nested method returns
    otel.spanInTrace(traceId, parentId, "child", 10);
    otel.spanWithIds(traceId, parentId, null, "parent", 20);
    otel.close();

    String json = traces.poll(5, TimeUnit.SECONDS);
    assertNotNull(json);
    Matcher m = SPAN.matcher(json);
    assertTrue(m.find(), json);
    assertEquals("child", m.group(4));
    assertEquals(traceId, m.group(1));
    assertEquals(parentId, m.group(3));
    assertTrue(m.find(), json);
    assertEquals("parent", m.group(4));
    assertEquals(traceId, m.group(1));
    assertEquals(parentId, m.group(2), "parent must carry the id its child referenced");
    assertNull(m.group(3), json);
  }

  @Test
  void eachInstanceIsADistinctWriter() throws Exception {
    OTelImpl first = start(context("otel.flush.interval.ms=60000"));
    OTelImpl second = start(context("otel.flush.interval.ms=60000"));
    first.counter("shared", 1);
    second.counter("shared", 1);
    first.close();
    second.close();

    Set<String> instanceIds = new HashSet<>();
    for (String json : drainAll(metrics)) {
      Matcher m =
          Pattern.compile(
                  "\\{\"key\":\"service\\.instance\\.id\",\"value\":\\{\"stringValue\":\"([0-9a-f-]{36})\"\\}\\}")
              .matcher(json);
      assertTrue(m.find(), json);
      instanceIds.add(m.group(1));
    }
    assertEquals(2, instanceIds.size(), instanceIds.toString());
  }

  @Test
  void exportsCountersAsDeltaSums() throws Exception {
    otel = start(context("otel.flush.interval.ms=100"));

    otel.counter("requests", 3);
    otel.counter("requests", 4);
    otel.counter("requests", -10);
    // The two increments may straddle an export; together they must add up to 7
    long total = 0;
    String last = null;
    while (total < 7) {
      last = pollContaining(metrics, "\"requests\"");
      assertTrue(last.contains("\"aggregationTemporality\":1,\"isMonotonic\":true"), last);
      total += Long.parseLong(dataPointValue(last, "requests"));
    }
    assertEquals(7, total);

    otel.counter("requests", 5);
    String next = pollContaining(metrics, "\"requests\"");
    // DELTA: only the increment since the previous export, not the running total of 12
    assertEquals("5", dataPointValue(next, "requests"), next);
    assertTrue(
        Long.parseLong(field(next, "startTimeUnixNano"))
            >= Long.parseLong(field(last, "timeUnixNano")),
        "delta windows must not overlap:\n" + last + "\n" + next);
  }

  @Test
  void exportsLastGaugeValue() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000"));
    otel.gauge("queue.depth", 1);
    otel.gauge("queue.depth", 42);
    otel.close();

    String json = pollContaining(metrics, "\"queue.depth\"");
    assertTrue(json.contains("\"gauge\":{\"dataPoints\":[{"), json);
    assertEquals("42", dataPointValue(json, "queue.depth"), json);
  }

  @Test
  void doesNotReexportUnchangedGauge() throws Exception {
    otel = start(context("otel.flush.interval.ms=100"));
    otel.gauge("queue.depth", 42);
    pollContaining(metrics, "\"queue.depth\"");

    Thread.sleep(400);
    for (String json : drainAll(metrics)) {
      assertFalse(json.contains("\"queue.depth\""), json);
    }
  }

  @Test
  void dropsSpansBeyondQueueCapacityAndReportsDrops() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000", "otel.queue.capacity=2"));
    for (int i = 0; i < 5; i++) {
      otel.span("s" + i, 1);
    }
    otel.close();

    assertEquals(2, countSpans(drainAll(traces)));
    String json = pollContaining(metrics, OTelImpl.SPANS_DROPPED_METRIC);
    assertEquals("3", dataPointValue(json, OTelImpl.SPANS_DROPPED_METRIC), json);
  }

  @Test
  void limitsDistinctMetricNames() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000", "otel.max.metrics=1"));
    otel.counter("a", 1);
    otel.counter("b", 1);
    otel.gauge("g1", 1);
    otel.gauge("g2", 1);
    otel.close();

    String json = metrics.poll(5, TimeUnit.SECONDS);
    assertNotNull(json);
    assertEquals("1", dataPointValue(json, "a"), json);
    assertEquals("1", dataPointValue(json, "g1"), json);
    assertFalse(json.contains("\"b\""), json);
    assertFalse(json.contains("\"g2\""), json);
    assertEquals("2", dataPointValue(json, OTelImpl.METRICS_DROPPED_METRIC), json);
  }

  @Test
  void closeExportsEverythingInBatches() throws Exception {
    otel = start(context("otel.flush.interval.ms=60000", "otel.batch.size=2"));
    for (int i = 0; i < 5; i++) {
      otel.span("s" + i, 1);
    }
    long t0 = System.nanoTime();
    otel.close();
    assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(3));

    List<String> requests = drainAll(traces);
    assertEquals(3, requests.size(), requests.toString());
    assertEquals(5, countSpans(requests));

    // Recording after close is a no-op
    otel.span("late", 1);
    otel.counter("late", 1);
    assertNull(traces.poll(200, TimeUnit.MILLISECONDS));
  }

  @Test
  void reportsFirstExportFailureToClientOnce() throws Exception {
    int closedPort;
    try (ServerSocket ss = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      closedPort = ss.getLocalPort();
    }
    TestContext ctx =
        context("otel.endpoint=http://127.0.0.1:" + closedPort + "/", "otel.flush.interval.ms=50");
    otel = start(ctx);
    for (int i = 0; i < 5; i++) {
      otel.span("s", 1);
      Thread.sleep(60);
    }
    otel.close();

    assertEquals(1, ctx.messages.size(), ctx.messages.toString());
    assertTrue(
        ctx.messages
            .get(0)
            .startsWith("btrace-otel: export failed: http://127.0.0.1:" + closedPort + "/v1/"),
        ctx.messages.get(0));
  }

  @Test
  void ignoresCallsBeforeInitializationAndNullNames() throws Exception {
    OTelImpl uninitialized = new OTelImpl();
    uninitialized.span("x", 1);
    uninitialized.counter("x", 1);
    uninitialized.gauge("x", 1);
    uninitialized.close();

    otel = start(context("otel.flush.interval.ms=60000"));
    otel.span(null, 1);
    otel.counter(null, 1);
    otel.gauge(null, 1);
    otel.close();
    assertTrue(drainAll(traces).isEmpty());
    assertTrue(drainAll(metrics).isEmpty());
  }

  @Test
  void fallsBackToSystemPropertiesWithoutContext() throws Exception {
    String key = "btrace.otel.endpoint";
    String previous = System.getProperty(key);
    System.setProperty(key, endpoint());
    try {
      otel = new OTelImpl();
      otel.initialize(null);
      otel.span("from-sysprop", 1);
      otel.close();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    String json = traces.poll(5, TimeUnit.SECONDS);
    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"from-sysprop\""), json);
  }

  private OTelImpl start(TestContext ctx) throws Exception {
    OTelImpl impl = new OTelImpl();
    impl.initialize(ctx);
    return impl;
  }

  private TestContext context(String... args) {
    List<String> all = new ArrayList<>();
    all.add("otel.endpoint=" + endpoint());
    for (String a : args) {
      all.add(a);
    }
    Map<String, String> map = new HashMap<>();
    for (String a : all) {
      int idx = a.indexOf('=');
      map.put(a.substring(0, idx), a.substring(idx + 1));
    }
    return new TestContext(new ArgsMap(map));
  }

  private String endpoint() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static void receive(InputStream body, BlockingQueue<String> sink, HttpExchange ex)
      throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = body.read(buf)) != -1) {
      bos.write(buf, 0, n);
    }
    sink.add(new String(bos.toByteArray(), StandardCharsets.UTF_8));
    ex.sendResponseHeaders(200, -1);
    ex.close();
  }

  private static String pollContaining(BlockingQueue<String> q, String needle) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      String json = q.poll(100, TimeUnit.MILLISECONDS);
      if (json != null && json.contains(needle)) {
        return json;
      }
    }
    throw new AssertionError("no export containing " + needle);
  }

  private static List<String> drainAll(BlockingQueue<String> q) {
    List<String> out = new ArrayList<>();
    q.drainTo(out);
    return out;
  }

  private static int countSpans(List<String> requests) {
    int count = 0;
    for (String json : requests) {
      Matcher m = SPAN.matcher(json);
      while (m.find()) {
        count++;
      }
    }
    return count;
  }

  private static String dataPointValue(String json, String metric) {
    Matcher m =
        Pattern.compile(
                "\\{\"name\":\""
                    + Pattern.quote(metric)
                    + "\",\"(?:sum|gauge)\":\\{\"dataPoints\":\\[\\{[^}]*\"asInt\":\"(-?\\d+)\"")
            .matcher(json);
    return m.find() ? m.group(1) : null;
  }

  private static String field(String json, String name) {
    Matcher m = Pattern.compile("\"" + name + "\":\"(\\d+)\"").matcher(json);
    assertTrue(m.find(), json);
    return m.group(1);
  }

  private static final class TestContext implements ExtensionContext {
    final List<String> messages = new CopyOnWriteArrayList<>();
    private final ArgsMap args;

    TestContext(ArgsMap args) {
      this.args = args;
    }

    @Override
    public void send(String message) {
      messages.add(message);
    }

    @Override
    public void send(Command command) {}

    @Override
    public ArgsMap getArgs() {
      return args;
    }

    @Override
    public String getScriptClassName() {
      return "TestScript";
    }

    @Override
    public PermissionSet getPermissions() {
      return PermissionSet.empty();
    }

    @Override
    public boolean hasPermission(Permission permission) {
      return true;
    }
  }
}
