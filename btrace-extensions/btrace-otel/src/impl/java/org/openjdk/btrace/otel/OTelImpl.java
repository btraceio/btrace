package org.openjdk.btrace.otel;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.ExtensionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry extension implementation.
 *
 * <p>Exports trace spans and metrics via OTLP/HTTP+JSON to a configurable collector endpoint.
 * Spans and metrics are buffered in lock-free queues and flushed by a background daemon thread.
 *
 * <p>Configuration (system properties):
 *
 * <ul>
 *   <li>{@code btrace.otel.endpoint} &mdash; OTLP HTTP base URL (default {@code
 *       http://localhost:4318})
 *   <li>{@code btrace.otel.service.name} &mdash; service resource name (default {@code btrace})
 *   <li>{@code btrace.otel.flush.interval.ms} &mdash; flush interval in ms (default {@code 5000})
 *   <li>{@code btrace.otel.batch.size} &mdash; max spans/metrics per flush (default {@code 256})
 * </ul>
 */
public final class OTelImpl extends Extension implements OTel {
  private static final Logger log = LoggerFactory.getLogger(OTelImpl.class);

  private static final String DEFAULT_ENDPOINT = "http://localhost:4318";
  private static final String DEFAULT_SERVICE_NAME = "btrace";
  private static final int DEFAULT_FLUSH_INTERVAL_MS = 5000;
  private static final int DEFAULT_BATCH_SIZE = 256;

  private String endpoint;
  private String serviceName;
  private int flushIntervalMs;
  private int batchSize;

  private final ConcurrentLinkedQueue<SpanRecord> spanQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<MetricRecord> metricQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile Thread exporterThread;
  private final Random random = new Random();

  @Override
  public void initialize(ExtensionContext ctx) throws ExtensionException {
    super.initialize(ctx);
    endpoint = System.getProperty("btrace.otel.endpoint", DEFAULT_ENDPOINT);
    serviceName = System.getProperty("btrace.otel.service.name", DEFAULT_SERVICE_NAME);
    flushIntervalMs =
        getIntProperty("btrace.otel.flush.interval.ms", DEFAULT_FLUSH_INTERVAL_MS);
    batchSize = getIntProperty("btrace.otel.batch.size", DEFAULT_BATCH_SIZE);

    running.set(true);
    Thread t = new Thread(this::exportLoop, "btrace-otel-exporter");
    t.setDaemon(true);
    t.start();
    exporterThread = t;
    log.debug("OTel extension initialized: endpoint={}, service={}", endpoint, serviceName);
  }

  @Override
  public void close() {
    running.set(false);
    Thread t = exporterThread;
    if (t != null) {
      t.interrupt();
      try {
        t.join(2000);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
    // Final flush
    flushSpans();
    flushMetrics();
  }

  // --- API methods (called from instrumented code, must be fast & safe) ---

  @Override
  public void span(String name, long durationNanos) {
    if (name == null || !running.get()) return;
    spanQueue.add(new SpanRecord(name, null, null, durationNanos));
  }

  @Override
  public void span(String name, String attrKey, String attrValue, long durationNanos) {
    if (name == null || !running.get()) return;
    spanQueue.add(new SpanRecord(name, attrKey, attrValue, durationNanos));
  }

  @Override
  public void counter(String name, long value) {
    if (name == null || !running.get()) return;
    metricQueue.add(new MetricRecord(name, value, MetricType.COUNTER));
  }

  @Override
  public void gauge(String name, long value) {
    if (name == null || !running.get()) return;
    metricQueue.add(new MetricRecord(name, value, MetricType.GAUGE));
  }

  // --- Background exporter ---

  private void exportLoop() {
    while (running.get()) {
      try {
        Thread.sleep(flushIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      flushSpans();
      flushMetrics();
    }
  }

  private void flushSpans() {
    List<SpanRecord> batch = drain(spanQueue, batchSize);
    if (batch.isEmpty()) return;
    try {
      String json = buildSpansJson(batch);
      post(endpoint + "/v1/traces", json);
    } catch (Throwable t) {
      log.debug("Failed to export spans", t);
    }
  }

  private void flushMetrics() {
    List<MetricRecord> batch = drain(metricQueue, batchSize);
    if (batch.isEmpty()) return;
    try {
      String json = buildMetricsJson(batch);
      post(endpoint + "/v1/metrics", json);
    } catch (Throwable t) {
      log.debug("Failed to export metrics", t);
    }
  }

  // --- OTLP JSON builders ---

  private String buildSpansJson(List<SpanRecord> spans) {
    long nowNanos = System.currentTimeMillis() * 1_000_000L;
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\"resourceSpans\":[{");
    appendResource(sb);
    sb.append(",\"scopeSpans\":[{\"scope\":{\"name\":\"btrace\"},\"spans\":[");
    for (int i = 0; i < spans.size(); i++) {
      if (i > 0) sb.append(',');
      SpanRecord r = spans.get(i);
      long endTime = nowNanos;
      long startTime = endTime - r.durationNanos;
      sb.append("{\"traceId\":\"").append(randomHex(32));
      sb.append("\",\"spanId\":\"").append(randomHex(16));
      sb.append("\",\"name\":\"").append(escapeJson(r.name));
      sb.append("\",\"kind\":1");
      sb.append(",\"startTimeUnixNano\":\"").append(startTime);
      sb.append("\",\"endTimeUnixNano\":\"").append(endTime).append('"');
      if (r.attrKey != null) {
        sb.append(",\"attributes\":[{\"key\":\"").append(escapeJson(r.attrKey));
        sb.append("\",\"value\":{\"stringValue\":\"").append(escapeJson(r.attrValue));
        sb.append("\"}}]");
      }
      sb.append(",\"status\":{}}");
    }
    sb.append("]}]}]}");
    return sb.toString();
  }

  private String buildMetricsJson(List<MetricRecord> metrics) {
    long nowNanos = System.currentTimeMillis() * 1_000_000L;
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\"resourceMetrics\":[{");
    appendResource(sb);
    sb.append(",\"scopeMetrics\":[{\"scope\":{\"name\":\"btrace\"},\"metrics\":[");
    for (int i = 0; i < metrics.size(); i++) {
      if (i > 0) sb.append(',');
      MetricRecord r = metrics.get(i);
      sb.append("{\"name\":\"").append(escapeJson(r.name)).append('"');
      if (r.type == MetricType.COUNTER) {
        sb.append(",\"sum\":{\"dataPoints\":[{\"asInt\":\"").append(r.value);
        sb.append("\",\"timeUnixNano\":\"").append(nowNanos);
        sb.append("\"}],\"isMonotonic\":true,\"aggregationTemporality\":2}");
      } else {
        sb.append(",\"gauge\":{\"dataPoints\":[{\"asInt\":\"").append(r.value);
        sb.append("\",\"timeUnixNano\":\"").append(nowNanos);
        sb.append("\"}]}");
      }
      sb.append('}');
    }
    sb.append("]}]}]}");
    return sb.toString();
  }

  private void appendResource(StringBuilder sb) {
    sb.append("\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"");
    sb.append(escapeJson(serviceName));
    sb.append("\"}}]}");
  }

  // --- HTTP transport ---

  private void post(String urlStr, String json) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
    try {
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      byte[] body = json.getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(body.length);
      try (OutputStream os = conn.getOutputStream()) {
        os.write(body);
      }
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        log.debug("OTLP export returned HTTP {}", code);
      }
    } finally {
      conn.disconnect();
    }
  }

  // --- Helpers ---

  private static int getIntProperty(String key, int defaultValue) {
    String val = System.getProperty(key);
    if (val != null) {
      try {
        return Integer.parseInt(val);
      } catch (NumberFormatException e) {
        // fall through
      }
    }
    return defaultValue;
  }

  private <T> List<T> drain(ConcurrentLinkedQueue<T> queue, int max) {
    List<T> list = new ArrayList<>(Math.min(max, 64));
    for (int i = 0; i < max; i++) {
      T item = queue.poll();
      if (item == null) break;
      list.add(item);
    }
    return list;
  }

  private String randomHex(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(Integer.toHexString(random.nextInt(16)));
    }
    return sb.toString();
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    StringBuilder sb = null;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String replacement = null;
      switch (c) {
        case '"':
          replacement = "\\\"";
          break;
        case '\\':
          replacement = "\\\\";
          break;
        case '\n':
          replacement = "\\n";
          break;
        case '\r':
          replacement = "\\r";
          break;
        case '\t':
          replacement = "\\t";
          break;
      }
      if (replacement != null) {
        if (sb == null) {
          sb = new StringBuilder(s.length() + 16);
          sb.append(s, 0, i);
        }
        sb.append(replacement);
      } else if (sb != null) {
        sb.append(c);
      }
    }
    return sb != null ? sb.toString() : s;
  }

  // --- Data records ---

  private static final class SpanRecord {
    final String name;
    final String attrKey;
    final String attrValue;
    final long durationNanos;

    SpanRecord(String name, String attrKey, String attrValue, long durationNanos) {
      this.name = name;
      this.attrKey = attrKey;
      this.attrValue = attrValue;
      this.durationNanos = durationNanos;
    }
  }

  private enum MetricType {
    COUNTER,
    GAUGE
  }

  private static final class MetricRecord {
    final String name;
    final long value;
    final MetricType type;

    MetricRecord(String name, long value, MetricType type) {
      this.name = name;
      this.value = value;
      this.type = type;
    }
  }
}
