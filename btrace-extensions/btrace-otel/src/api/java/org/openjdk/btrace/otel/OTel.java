package org.openjdk.btrace.otel;

import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * BTrace extension API for exporting trace spans and metrics via the OpenTelemetry Protocol (OTLP).
 *
 * <p>Spans are batched and exported asynchronously over HTTP to a configurable OTLP endpoint
 * (defaults to {@code http://localhost:4318}). Use system properties to configure:
 *
 * <ul>
 *   <li>{@code btrace.otel.endpoint} &mdash; OTLP HTTP endpoint (default {@code
 *       http://localhost:4318})
 *   <li>{@code btrace.otel.service.name} &mdash; service name reported in resource attributes
 *       (default {@code btrace})
 * </ul>
 *
 * <p>Example BTrace script usage:
 *
 * <pre>{@code
 * @Injected(ServiceType.RUNTIME)
 * private static OTel otel;
 *
 * @OnMethod(clazz = "com.example.MyService", method = "handle")
 * public static void onHandle(@Duration long dur) {
 *     otel.span("MyService.handle", dur);
 * }
 *
 * @OnMethod(clazz = "com.example.MyService", method = "process")
 * public static void onProcess(@Duration long dur) {
 *     otel.span("process", "component", "processor", dur);
 * }
 * }</pre>
 */
@ServiceDescriptor(permissions = {Permission.NETWORK})
public interface OTel {

  /**
   * Records a completed trace span with the given name and duration.
   *
   * @param name the span name (e.g. method or operation name)
   * @param durationNanos span duration in nanoseconds
   */
  void span(String name, long durationNanos);

  /**
   * Records a completed trace span with one attribute key-value pair.
   *
   * @param name the span name
   * @param attrKey attribute key
   * @param attrValue attribute value
   * @param durationNanos span duration in nanoseconds
   */
  void span(String name, String attrKey, String attrValue, long durationNanos);

  /**
   * Records a counter increment.
   *
   * @param name the metric name
   * @param value the increment value
   */
  void counter(String name, long value);

  /**
   * Records a gauge value.
   *
   * @param name the metric name
   * @param value the current gauge value
   */
  void gauge(String name, long value);
}
