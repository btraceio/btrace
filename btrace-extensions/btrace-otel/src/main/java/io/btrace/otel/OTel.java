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

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.ServiceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exports spans and metrics from BTrace scripts to an OpenTelemetry collector using OTLP/HTTP with
 * JSON encoding.
 *
 * <p>Recording is lock-free once a metric name has been seen: spans go to a bounded queue (dropped
 * when full), counters and gauges are aggregated per name in memory, and a background thread posts
 * them to {@code <endpoint>/v1/traces} and {@code <endpoint>/v1/metrics} once per flush interval.
 * Counters are exported as monotonic sums with DELTA temporality: each export carries the sum of
 * the increments recorded since the previous export. Gauges export the last value recorded since
 * the previous export.
 *
 * <p>Every instance (one per script) reports a random {@code service.instance.id} resource
 * attribute, so concurrent scripts or JVMs exporting the same metric names are distinct writers.
 *
 * <p>Configuration is read from script arguments first, then from system properties prefixed with
 * {@code btrace.} (for example {@code btrace.otel.endpoint}):
 *
 * <ul>
 *   <li>{@code otel.endpoint} &mdash; OTLP/HTTP base URL (default {@code http://localhost:4318})
 *   <li>{@code otel.service.name} &mdash; {@code service.name} resource attribute (default {@code
 *       btrace})
 *   <li>{@code otel.flush.interval.ms} &mdash; export interval (default {@code 5000})
 *   <li>{@code otel.batch.size} &mdash; maximum spans per request (default {@code 512})
 *   <li>{@code otel.queue.capacity} &mdash; maximum buffered spans, rounded up to a power of two
 *       (default {@code 2048})
 *   <li>{@code otel.max.metrics} &mdash; maximum distinct counter and gauge names (default {@code
 *       1000})
 * </ul>
 *
 * <pre>
 * &#64;Injected private static OTel otel;
 *
 * &#64;OnMethod(clazz = "com.example.MyService", method = "handle",
 *           location = &#64;Location(Kind.RETURN))
 * public static void onHandle(&#64;Duration long dur) {
 *   otel.span("MyService.handle", dur);
 *   otel.counter("myservice.handle.calls", 1);
 * }
 * </pre>
 *
 * <p>Nesting spans recorded by the same script: allocate the parent's ids when the parent starts,
 * hand them to children, and record the parent with the same ids when it ends.
 *
 * <pre>
 * &#64;TLS private static String traceId;
 * &#64;TLS private static String requestSpanId;
 *
 * &#64;OnMethod(clazz = "com.example.Api", method = "handle")
 * public static void onEnter() {
 *   traceId = otel.newTraceId();
 *   requestSpanId = otel.newSpanId();
 * }
 *
 * &#64;OnMethod(clazz = "com.example.Dao", method = "query", location = &#64;Location(Kind.RETURN))
 * public static void onQuery(&#64;Duration long dur) {
 *   otel.spanInTrace(traceId, requestSpanId, "Dao.query", dur);
 * }
 *
 * &#64;OnMethod(clazz = "com.example.Api", method = "handle", location = &#64;Location(Kind.RETURN))
 * public static void onExit(&#64;Duration long dur) {
 *   otel.spanWithIds(traceId, requestSpanId, null, "Api.handle", dur);
 * }
 * </pre>
 */
@ServiceDescriptor(permissions = {Permission.NETWORK, Permission.THREADS, Permission.SYSTEM_PROPS})
public interface OTel {

  /**
   * Records a completed span ending now.
   *
   * @param name the span name; ignored when {@code null}
   * @param durationNanos the span duration in nanoseconds
   */
  void span(@NotNull String name, long durationNanos);

  /**
   * Records a completed span ending now, with one string attribute.
   *
   * @param name the span name; ignored when {@code null}
   * @param attrKey the attribute key; no attribute is attached when {@code null}
   * @param attrValue the attribute value
   * @param durationNanos the span duration in nanoseconds
   */
  void span(
      @NotNull String name,
      @Nullable String attrKey,
      @Nullable String attrValue,
      long durationNanos);

  /**
   * Records a completed span ending now, as part of a trace identified by the caller.
   *
   * <p>Use this to correlate BTrace spans with the application's own tracing, for example with ids
   * taken from a W3C {@code traceparent} header or from the application's current span context. Ids
   * follow the W3C Trace Context format: lowercase or uppercase hex, not all zeros. A missing or
   * malformed {@code traceId} is replaced by a fresh random trace id; a missing or malformed {@code
   * parentSpanId} makes the span a root span.
   *
   * @param traceId the 32-hex-digit trace id
   * @param parentSpanId the 16-hex-digit id of the parent span
   * @param name the span name; ignored when {@code null}
   * @param durationNanos the span duration in nanoseconds
   */
  void spanInTrace(
      @Nullable String traceId,
      @Nullable String parentSpanId,
      @NotNull String name,
      long durationNanos);

  /**
   * Records a completed span ending now with caller-chosen ids, so that the script can refer to it
   * as the parent of other spans. Pair with {@link #newTraceId()} and {@link #newSpanId()}.
   *
   * <p>Ids follow the same rules as {@link #spanInTrace}; a missing or malformed {@code spanId} is
   * replaced by a fresh random span id.
   *
   * @param traceId the 32-hex-digit trace id
   * @param spanId the 16-hex-digit id of this span
   * @param parentSpanId the 16-hex-digit id of the parent span
   * @param name the span name; ignored when {@code null}
   * @param durationNanos the span duration in nanoseconds
   */
  void spanWithIds(
      @Nullable String traceId,
      @Nullable String spanId,
      @Nullable String parentSpanId,
      @NotNull String name,
      long durationNanos);

  /**
   * Generates a random W3C trace id.
   *
   * @return 32 lowercase hex digits; {@code null} only from a no-op shim of an optional injection
   */
  @Nullable String newTraceId();

  /**
   * Generates a random W3C span id.
   *
   * @return 16 lowercase hex digits; {@code null} only from a no-op shim of an optional injection
   */
  @Nullable String newSpanId();

  /**
   * Adds {@code value} to the counter {@code name}.
   *
   * @param name the metric name; ignored when {@code null}
   * @param value the increment; negative values are ignored because the sum is monotonic
   */
  void counter(@NotNull String name, long value);

  /**
   * Sets the gauge {@code name} to {@code value}.
   *
   * @param name the metric name; ignored when {@code null}
   * @param value the current value
   */
  void gauge(@NotNull String name, long value);
}
