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

import io.btrace.core.ArgsMap;
import io.btrace.core.extensions.Extension;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.extensions.ExtensionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link OTel} implementation exporting OTLP/HTTP JSON from a single background thread.
 *
 * <p>The recording methods run on instrumented application threads and never block or perform I/O.
 * One instance exists per script; {@link #close()} stops the exporter after a final, time-bounded
 * export.
 */
public final class OTelImpl extends Extension implements OTel {
  static final String DEFAULT_ENDPOINT = "http://localhost:4318";
  static final String DEFAULT_SERVICE_NAME = "btrace";
  static final int DEFAULT_FLUSH_INTERVAL_MS = 5000;
  static final int DEFAULT_BATCH_SIZE = 512;
  static final int DEFAULT_QUEUE_CAPACITY = 2048;
  static final int DEFAULT_MAX_METRICS = 1000;

  static final String SPANS_DROPPED_METRIC = "btrace.otel.spans.dropped";
  static final String METRICS_DROPPED_METRIC = "btrace.otel.metric.updates.dropped";

  // opentelemetry/proto/trace/v1/trace.proto: SPAN_KIND_INTERNAL
  private static final int SPAN_KIND_INTERNAL = 1;
  // opentelemetry/proto/metrics/v1/metrics.proto: AGGREGATION_TEMPORALITY_DELTA
  private static final int AGGREGATION_TEMPORALITY_DELTA = 1;

  private static final int HTTP_TIMEOUT_MS = 5000;
  private static final long CLOSE_EXPORT_BUDGET_MS = 2000;

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean failureReported = new AtomicBoolean();
  private final CountDownLatch stopSignal = new CountDownLatch(1);
  private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> gauges = new ConcurrentHashMap<>();
  private final AtomicLong droppedSpans = new AtomicLong();
  private final AtomicLong droppedMetricUpdates = new AtomicLong();

  private ExtensionContext ctx;
  private String endpoint;
  private String serviceName;
  private int flushIntervalMs;
  private int batchSize;
  private int maxMetrics;
  private ArrayBlockingQueue<SpanRecord> spans;
  private long epochBaseNanos;
  private long monotonicBaseNanos;
  // Only accessed from the exporter thread after initialization
  private long lastMetricExportNanos;
  private Thread exporter;

  // Written last in initialize(); readers check it before touching the fields above
  private volatile boolean running;

  @Override
  public void initialize(ExtensionContext ctx) throws ExtensionException {
    super.initialize(ctx);
    this.ctx = ctx;
    ArgsMap args = ctx != null ? ctx.getArgs() : null;
    endpoint = stripTrailingSlash(setting(args, "otel.endpoint", DEFAULT_ENDPOINT));
    serviceName = setting(args, "otel.service.name", DEFAULT_SERVICE_NAME);
    flushIntervalMs = intSetting(args, "otel.flush.interval.ms", DEFAULT_FLUSH_INTERVAL_MS);
    batchSize = intSetting(args, "otel.batch.size", DEFAULT_BATCH_SIZE);
    maxMetrics = intSetting(args, "otel.max.metrics", DEFAULT_MAX_METRICS);
    spans =
        new ArrayBlockingQueue<>(intSetting(args, "otel.queue.capacity", DEFAULT_QUEUE_CAPACITY));

    epochBaseNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    monotonicBaseNanos = System.nanoTime();
    lastMetricExportNanos = epochBaseNanos;

    Thread t = new Thread(this::runExporter, "btrace-otel-exporter");
    t.setDaemon(true);
    // Do not pin the loader of whichever application thread triggered the injection
    t.setContextClassLoader(null);
    exporter = t;
    running = true;
    t.start();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    running = false;
    stopSignal.countDown();
    Thread t = exporter;
    if (t != null && t != Thread.currentThread()) {
      try {
        t.join(CLOSE_EXPORT_BUDGET_MS + 500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void span(String name, long durationNanos) {
    span(name, null, null, durationNanos);
  }

  @Override
  public void span(String name, String attrKey, String attrValue, long durationNanos) {
    record(null, null, name, attrKey, attrValue, durationNanos);
  }

  @Override
  public void spanInTrace(String traceId, String parentSpanId, String name, long durationNanos) {
    record(traceId, parentSpanId, name, null, null, durationNanos);
  }

  private void record(
      String traceId,
      String parentSpanId,
      String name,
      String attrKey,
      String attrValue,
      long durationNanos) {
    if (name == null || !running) {
      return;
    }
    // Ids are validated on the exporter thread to keep the instrumented path to a queue offer
    SpanRecord r =
        new SpanRecord(traceId, parentSpanId, name, attrKey, attrValue, nowNanos(), durationNanos);
    if (!spans.offer(r)) {
      droppedSpans.incrementAndGet();
    }
  }

  @Override
  public void counter(String name, long value) {
    if (name == null || value < 0 || !running) {
      return;
    }
    AtomicLong sum = counters.get(name);
    if (sum == null) {
      if (counters.size() >= maxMetrics) {
        droppedMetricUpdates.incrementAndGet();
        return;
      }
      AtomicLong created = new AtomicLong();
      sum = counters.putIfAbsent(name, created);
      if (sum == null) {
        sum = created;
      }
    }
    sum.addAndGet(value);
  }

  @Override
  public void gauge(String name, long value) {
    if (name == null || !running) {
      return;
    }
    if (gauges.size() >= maxMetrics && !gauges.containsKey(name)) {
      droppedMetricUpdates.incrementAndGet();
      return;
    }
    gauges.put(name, value);
  }

  private void runExporter() {
    try {
      while (!stopSignal.await(flushIntervalMs, TimeUnit.MILLISECONDS)) {
        exportAll(0);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    exportAll(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_EXPORT_BUDGET_MS));
  }

  /**
   * Exports everything recorded so far.
   *
   * @param deadline {@link System#nanoTime()} after which no further request is started, or 0 for
   *     no deadline
   */
  private void exportAll(long deadline) {
    try {
      exportSpans(deadline);
      exportMetrics(deadline);
    } catch (Throwable t) {
      reportFailure("unexpected exporter error: " + t);
    }
  }

  private void exportSpans(long deadline) {
    List<SpanRecord> batch = new ArrayList<>(Math.min(batchSize, 64));
    while (spans.drainTo(batch, batchSize) > 0) {
      if (deadline != 0 && System.nanoTime() - deadline >= 0) {
        // Out of time on shutdown; account for what will not be sent
        droppedSpans.addAndGet(batch.size() + spans.size());
        spans.clear();
        return;
      }
      post("/v1/traces", spansJson(batch), deadline);
      batch.clear();
    }
  }

  private void exportMetrics(long deadline) {
    long start = lastMetricExportNanos;
    long now = nowNanos();
    lastMetricExportNanos = now;

    StringBuilder metrics = new StringBuilder(256);
    for (Map.Entry<String, AtomicLong> e : counters.entrySet()) {
      appendSum(metrics, e.getKey(), e.getValue().getAndSet(0), start, now);
    }
    appendSum(metrics, SPANS_DROPPED_METRIC, droppedSpans.getAndSet(0), start, now);
    appendSum(metrics, METRICS_DROPPED_METRIC, droppedMetricUpdates.getAndSet(0), start, now);
    for (Map.Entry<String, Long> e : gauges.entrySet()) {
      String name = e.getKey();
      Long value = e.getValue();
      // Report each gauge once per update; a concurrent update keeps the entry for the next export
      gauges.remove(name, value);
      appendGauge(metrics, name, value, now);
    }
    if (metrics.length() == 0) {
      return;
    }
    if (deadline != 0 && System.nanoTime() - deadline >= 0) {
      return;
    }
    StringBuilder sb = new StringBuilder(metrics.length() + 256);
    sb.append("{\"resourceMetrics\":[{");
    appendResource(sb);
    sb.append(",\"scopeMetrics\":[{");
    appendScope(sb);
    sb.append(",\"metrics\":[").append(metrics).append("]}]}]}");
    post("/v1/metrics", sb.toString(), deadline);
  }

  private String spansJson(List<SpanRecord> batch) {
    StringBuilder sb = new StringBuilder(256 + batch.size() * 192);
    sb.append("{\"resourceSpans\":[{");
    appendResource(sb);
    sb.append(",\"scopeSpans\":[{");
    appendScope(sb);
    sb.append(",\"spans\":[");
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < batch.size(); i++) {
      SpanRecord r = batch.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append("{\"traceId\":\"");
      String traceId = normalizeId(r.traceId, 32);
      if (traceId != null) {
        sb.append(traceId);
      } else {
        appendHex(sb, nonZero(rnd.nextLong()));
        appendHex(sb, rnd.nextLong());
      }
      sb.append("\",\"spanId\":\"");
      appendHex(sb, nonZero(rnd.nextLong()));
      sb.append('"');
      String parentSpanId = normalizeId(r.parentSpanId, 16);
      if (parentSpanId != null) {
        sb.append(",\"parentSpanId\":\"").append(parentSpanId).append('"');
      }
      sb.append(",\"name\":");
      appendString(sb, r.name);
      sb.append(",\"kind\":").append(SPAN_KIND_INTERNAL);
      sb.append(",\"startTimeUnixNano\":\"").append(r.endNanos - Math.max(0, r.durationNanos));
      sb.append("\",\"endTimeUnixNano\":\"").append(r.endNanos).append('"');
      if (r.attrKey != null) {
        sb.append(",\"attributes\":[{\"key\":");
        appendString(sb, r.attrKey);
        sb.append(",\"value\":{\"stringValue\":");
        appendString(sb, r.attrValue != null ? r.attrValue : "");
        sb.append("}}]");
      }
      sb.append('}');
    }
    sb.append("]}]}]}");
    return sb.toString();
  }

  private static void appendSum(StringBuilder sb, String name, long delta, long start, long now) {
    if (delta == 0) {
      return;
    }
    if (sb.length() > 0) {
      sb.append(',');
    }
    sb.append("{\"name\":");
    appendString(sb, name);
    sb.append(",\"sum\":{\"dataPoints\":[{\"startTimeUnixNano\":\"").append(start);
    sb.append("\",\"timeUnixNano\":\"").append(now);
    sb.append("\",\"asInt\":\"").append(delta);
    sb.append("\"}],\"aggregationTemporality\":").append(AGGREGATION_TEMPORALITY_DELTA);
    sb.append(",\"isMonotonic\":true}}");
  }

  private static void appendGauge(StringBuilder sb, String name, long value, long now) {
    if (sb.length() > 0) {
      sb.append(',');
    }
    sb.append("{\"name\":");
    appendString(sb, name);
    sb.append(",\"gauge\":{\"dataPoints\":[{\"timeUnixNano\":\"").append(now);
    sb.append("\",\"asInt\":\"").append(value).append("\"}]}}");
  }

  private void appendResource(StringBuilder sb) {
    sb.append("\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{");
    sb.append("\"stringValue\":");
    appendString(sb, serviceName);
    sb.append("}}]}");
  }

  private static void appendScope(StringBuilder sb) {
    sb.append("\"scope\":{\"name\":\"io.btrace.otel\"}");
  }

  private void post(String path, String json, long deadline) {
    String url = endpoint + path;
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(url).openConnection();
      int timeout = HTTP_TIMEOUT_MS;
      if (deadline != 0) {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        timeout = (int) Math.max(1, Math.min(timeout, remaining));
      }
      conn.setConnectTimeout(timeout);
      conn.setReadTimeout(timeout);
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setUseCaches(false);
      conn.setRequestProperty("Content-Type", "application/json");
      byte[] body = json.getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(body.length);
      try (OutputStream os = conn.getOutputStream()) {
        os.write(body);
      }
      int code = conn.getResponseCode();
      // Consume the response so the connection can be reused
      drain(code < 400 ? conn.getInputStream() : conn.getErrorStream());
      if (code < 200 || code >= 300) {
        reportFailure("HTTP " + code + " from " + url);
      }
    } catch (Throwable t) {
      if (conn != null) {
        conn.disconnect();
      }
      reportFailure(url + ": " + t);
    }
  }

  private static void drain(InputStream is) throws IOException {
    if (is == null) {
      return;
    }
    try (InputStream in = is) {
      byte[] buf = new byte[512];
      while (in.read(buf) != -1) {
        // discard
      }
    }
  }

  private void reportFailure(String reason) {
    ExtensionContext c = ctx;
    if (c != null && failureReported.compareAndSet(false, true)) {
      c.send("btrace-otel: export failed: " + reason + " (further failures are not reported)");
    }
  }

  private long nowNanos() {
    return epochBaseNanos + (System.nanoTime() - monotonicBaseNanos);
  }

  /**
   * Returns {@code id} in lowercase if it is a valid W3C Trace Context id of {@code length} hex
   * digits that are not all zero, otherwise {@code null}.
   */
  private static String normalizeId(String id, int length) {
    if (id == null || id.length() != length) {
      return null;
    }
    boolean nonZero = false;
    for (int i = 0; i < length; i++) {
      char c = id.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) {
        return null;
      }
      nonZero |= c != '0';
    }
    return nonZero ? id.toLowerCase(Locale.ROOT) : null;
  }

  private static long nonZero(long v) {
    return v != 0 ? v : 1;
  }

  private static void appendHex(StringBuilder sb, long v) {
    for (int shift = 60; shift >= 0; shift -= 4) {
      sb.append(HEX[(int) (v >>> shift) & 0xf]);
    }
  }

  private static void appendString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xf]);
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }

  private static String setting(ArgsMap args, String key, String defaultValue) {
    String value = args != null ? args.get(key) : null;
    if (value == null || value.trim().isEmpty()) {
      value = System.getProperty("btrace." + key);
    }
    return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
  }

  private static int intSetting(ArgsMap args, String key, int defaultValue) {
    try {
      int value = Integer.parseInt(setting(args, key, String.valueOf(defaultValue)));
      return value > 0 ? value : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String stripTrailingSlash(String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static final class SpanRecord {
    final String traceId;
    final String parentSpanId;
    final String name;
    final String attrKey;
    final String attrValue;
    final long endNanos;
    final long durationNanos;

    SpanRecord(
        String traceId,
        String parentSpanId,
        String name,
        String attrKey,
        String attrValue,
        long endNanos,
        long durationNanos) {
      this.traceId = traceId;
      this.parentSpanId = parentSpanId;
      this.name = name;
      this.attrKey = attrKey;
      this.attrValue = attrValue;
      this.endNanos = endNanos;
      this.durationNanos = durationNanos;
    }
  }
}
