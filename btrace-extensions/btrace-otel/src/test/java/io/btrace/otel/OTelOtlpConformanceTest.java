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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Decodes the exporter's payloads with the generated OTLP protobuf classes and a strict proto3 JSON
 * parser (unknown fields and wrong types are rejected), then checks the decoded semantics.
 */
class OTelOtlpConformanceTest {
  // OTLP/JSON encodes trace and span ids as hex, where proto3 JSON expects base64 for bytes
  private static final Pattern HEX_ID =
      Pattern.compile("\"(traceId|spanId|parentSpanId)\":\"([0-9a-fA-F]*)\"");

  private HttpServer server;
  private final List<String> traces = new CopyOnWriteArrayList<>();
  private final List<String> metrics = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startCollector() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/v1/traces", ex -> receive(ex, traces));
    server.createContext("/v1/metrics", ex -> receive(ex, metrics));
    server.start();
  }

  @AfterEach
  void stopCollector() {
    server.stop(0);
  }

  @Test
  void tracePayloadsDecodeAsOtlp() throws Exception {
    OTelImpl otel = start("otel.service.name=svc");
    otel.span("plain", 1_000);
    otel.span("attr", "k", "v", 2_000);
    otel.spanInTrace("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", "linked", 3_000);
    otel.close();

    List<Span> spans = new ArrayList<>();
    for (String json : traces) {
      ExportTraceServiceRequest.Builder req = ExportTraceServiceRequest.newBuilder();
      parse(json, req);
      assertEquals(1, req.getResourceSpansCount());
      assertEquals(
          "svc", attr(req.getResourceSpans(0).getResource().getAttributesList(), "service.name"));
      assertEquals("io.btrace.otel", req.getResourceSpans(0).getScopeSpans(0).getScope().getName());
      spans.addAll(req.getResourceSpans(0).getScopeSpans(0).getSpansList());
    }
    assertEquals(3, spans.size());
    for (Span s : spans) {
      assertEquals(16, s.getTraceId().size(), s.toString());
      assertEquals(8, s.getSpanId().size(), s.toString());
      assertEquals(Span.SpanKind.SPAN_KIND_INTERNAL, s.getKind());
      assertTrue(s.getEndTimeUnixNano() >= s.getStartTimeUnixNano());
    }
    Span attr = find(spans, "attr");
    assertEquals(2_000, attr.getEndTimeUnixNano() - attr.getStartTimeUnixNano());
    assertEquals("v", attr(attr.getAttributesList(), "k"));
    Span linked = find(spans, "linked");
    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", hex(linked.getTraceId().toByteArray()));
    assertEquals("00f067aa0ba902b7", hex(linked.getParentSpanId().toByteArray()));
    assertEquals(0, find(spans, "plain").getParentSpanId().size());
  }

  @Test
  void metricPayloadsDecodeAsOtlpWithDeltaWindows() throws Exception {
    OTelImpl otel = start("otel.flush.interval.ms=150");
    otel.counter("requests", 2);
    otel.gauge("depth", 7);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (metrics.isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    otel.counter("requests", 3);
    otel.close();

    List<NumberDataPoint> requests = new ArrayList<>();
    NumberDataPoint depth = null;
    for (String json : metrics) {
      ExportMetricsServiceRequest.Builder req = ExportMetricsServiceRequest.newBuilder();
      parse(json, req);
      for (Metric m : req.getResourceMetrics(0).getScopeMetrics(0).getMetricsList()) {
        if (m.getName().equals("requests")) {
          assertTrue(m.hasSum());
          assertTrue(m.getSum().getIsMonotonic());
          assertEquals(
              AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
              m.getSum().getAggregationTemporality());
          requests.addAll(m.getSum().getDataPointsList());
        } else if (m.getName().equals("depth")) {
          assertTrue(m.hasGauge());
          depth = m.getGauge().getDataPoints(0);
        }
      }
    }
    assertNotNull(depth);
    assertEquals(7, depth.getAsInt());
    assertEquals(2, requests.size(), requests.toString());
    assertEquals(2, requests.get(0).getAsInt());
    assertEquals(3, requests.get(1).getAsInt());
    // Data model: a delta point's start time equals the preceding point's time
    assertEquals(requests.get(0).getTimeUnixNano(), requests.get(1).getStartTimeUnixNano());
  }

  /**
   * The API only records completed spans. A span whose work starts on one thread and ends on
   * another can be recorded by the thread that observes the end, given the duration measured from
   * the start; the exported start time then lies on the starting thread's timeline.
   */
  @Test
  void spanStartedOnOneThreadCanBeRecordedOnAnother() throws Exception {
    OTelImpl otel = start();
    long[] startedAt = new long[2];
    Thread starter =
        new Thread(
            () -> {
              startedAt[0] = System.nanoTime();
              startedAt[1] = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
            });
    starter.start();
    starter.join();
    Thread.sleep(50);
    long[] recordedBy = new long[1];
    Thread finisher =
        new Thread(
            () -> {
              recordedBy[0] = Thread.currentThread().getId();
              otel.span("handoff", System.nanoTime() - startedAt[0]);
            });
    finisher.start();
    finisher.join();
    otel.close();

    ExportTraceServiceRequest.Builder req = ExportTraceServiceRequest.newBuilder();
    parse(traces.get(0), req);
    Span s = req.getResourceSpans(0).getScopeSpans(0).getSpans(0);
    // Wall-clock start within 20ms of the starter thread's clock reading (ms granularity)
    assertTrue(
        Math.abs(s.getStartTimeUnixNano() - startedAt[1]) < TimeUnit.MILLISECONDS.toNanos(20),
        "start=" + s.getStartTimeUnixNano() + " startedAt=" + startedAt[1]);
    assertTrue(
        s.getEndTimeUnixNano() - s.getStartTimeUnixNano() >= TimeUnit.MILLISECONDS.toNanos(50));
    assertTrue(recordedBy[0] != starter.getId());
  }

  private OTelImpl start(String... args) throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("otel.endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
    map.put("otel.flush.interval.ms", "60000");
    for (String a : args) {
      int idx = a.indexOf('=');
      map.put(a.substring(0, idx), a.substring(idx + 1));
    }
    OTelImpl otel = new OTelImpl();
    otel.initialize(new Ctx(new ArgsMap(map)));
    return otel;
  }

  private static void parse(String otlpJson, Message.Builder builder) throws IOException {
    StringBuffer sb = new StringBuffer();
    Matcher m = HEX_ID.matcher(otlpJson);
    while (m.find()) {
      String b64 = Base64.getEncoder().encodeToString(unhex(m.group(2)));
      m.appendReplacement(sb, "\"" + m.group(1) + "\":\"" + b64 + "\"");
    }
    m.appendTail(sb);
    // Strict: no ignoringUnknownFields()
    JsonFormat.parser().merge(sb.toString(), builder);
  }

  private static byte[] unhex(String hex) {
    byte[] out = new byte[hex.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
    }
    return out;
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static String attr(List<KeyValue> attrs, String key) {
    for (KeyValue kv : attrs) {
      if (kv.getKey().equals(key)) {
        return kv.getValue().getStringValue();
      }
    }
    return null;
  }

  private static Span find(List<Span> spans, String name) {
    for (Span s : spans) {
      if (s.getName().equals(name)) {
        return s;
      }
    }
    throw new AssertionError("no span " + name + " in " + spans);
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

  private static final class Ctx implements ExtensionContext {
    private final ArgsMap args;

    Ctx(ArgsMap args) {
      this.args = args;
    }

    @Override
    public void send(String message) {}

    @Override
    public void send(Command command) {}

    @Override
    public ArgsMap getArgs() {
      return args;
    }

    @Override
    public String getScriptClassName() {
      return "Conformance";
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
