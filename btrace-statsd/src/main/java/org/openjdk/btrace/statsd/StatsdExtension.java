/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.statsd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.ExtensionDescriptor;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.RequiresPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StatsD extension for BTrace.
 *
 * <p>A simple way to submit <a href="https://github.com/etsy/statsd/">statsd</a> metrics.
 *
 * <p>Use the following code to obtain an instance:
 *
 * <pre>
 * <code>
 * {@literal @}Injected(ServiceType.EXTENSION)
 * private static StatsdExtension statsd;
 * </code>
 * </pre>
 */
@ExtensionDescriptor(
    name = "statsd",
    version = "1.0",
    description = "StatsD metrics client for BTrace")
@RequiresPermission(value = Permission.NETWORK, reason = "Sends metrics via UDP")
@RequiresPermission(value = Permission.THREADS, reason = "Background sender thread")
public final class StatsdExtension extends Extension {
  private static final Logger log = LoggerFactory.getLogger(StatsdExtension.class);
  private static final Charset CHARSET = StandardCharsets.US_ASCII;

  private final QManager qManager = new QManager();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile ExecutorService executor;
  private volatile DatagramSocket socket;

  @Override
  public void initialize(ExtensionContext ctx) {
    super.initialize(ctx);

    if (!running.compareAndSet(false, true)) {
      return; // Already initialized
    }

    executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "BTrace StatsD Client - " + ctx.getScriptClassName());
              t.setDaemon(true);
              return t;
            });

    executor.submit(this::senderLoop);
  }

  @SuppressWarnings("InfiniteLoopStatement")
  private void senderLoop() {
    boolean entered = BTraceRuntime.enter();
    try {
      socket = new DatagramSocket();
      DatagramPacket dp = new DatagramPacket(new byte[0], 0);
      try {
        dp.setAddress(InetAddress.getByName(SharedSettings.GLOBAL.getStatsdHost()));
      } catch (UnknownHostException e) {
        log.warn("Invalid StatsD host: {}, using loopback", SharedSettings.GLOBAL.getStatsdHost());
        dp.setAddress(InetAddress.getLoopbackAddress());
      } catch (SecurityException e) {
        dp.setAddress(InetAddress.getLoopbackAddress());
      }
      dp.setPort(SharedSettings.GLOBAL.getStatsdPort());

      while (running.get()) {
        try {
          Collection<String> msgs = new ArrayList<>();
          String first = qManager.getQ().poll(100, TimeUnit.MILLISECONDS);
          if (first == null) {
            continue;
          }
          msgs.add(first);
          qManager.getQ().drainTo(msgs);

          StringBuilder sb = new StringBuilder();
          for (String m : msgs) {
            if (sb.length() + m.length() < 511) {
              sb.append(m).append('\n');
            } else {
              dp.setData(sb.toString().getBytes(CHARSET));
              socket.send(dp);
              sb.setLength(0);
            }
          }
          if (sb.length() > 0) {
            dp.setData(sb.toString().getBytes(CHARSET));
            socket.send(dp);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    } catch (IOException e) {
      log.error("StatsD client error", e);
    } finally {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
      if (entered) {
        BTraceRuntime.leave();
      }
    }
  }

  @Override
  public void close() {
    if (!running.compareAndSet(true, false)) {
      return; // Already closed
    }

    if (executor != null) {
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    if (socket != null && !socket.isClosed()) {
      socket.close();
    }
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   */
  public void increment(String name) {
    qManager.delta(name, 1, 0.0d, null);
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags.
   */
  public void increment(String name, String tags) {
    qManager.delta(name, 1, 0.0d, tags);
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed
   */
  public void increment(String name, double sampleRate) {
    qManager.delta(name, 1, sampleRate, null);
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed
   * @param tags Only for DogStatsD compatible collectors
   */
  public void increment(String name, double sampleRate, String tags) {
    qManager.delta(name, 1, sampleRate, tags);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   */
  public void decrement(String name) {
    qManager.decrement(name);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   * @param tags Only for DogStatsD compatible collectors
   */
  public void decrement(String name, String tags) {
    qManager.delta(name, -1, 0.0d, tags);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed
   */
  public void decrement(String name, double sampleRate) {
    qManager.delta(name, -1, sampleRate, null);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed
   * @param tags Only for DogStatsD compatible collectors
   */
  public void decrement(String name, double sampleRate, String tags) {
    qManager.delta(name, -1, sampleRate, tags);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   */
  public void count(String name, long count) {
    count(name, count, null);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   * @param tags Only for DogStatsD compatible collectors
   */
  public void count(String name, long count, String tags) {
    count(name, count, 0d, tags);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   * @param sampleRate the sampling rate being employed
   */
  public void count(String name, long count, double sampleRate) {
    count(name, count, sampleRate, null);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   * @param sampleRate the sampling rate being employed
   * @param tags Only for DogStatsD compatible collectors
   */
  public void count(String name, long count, double sampleRate, String tags) {
    qManager.submit(name, count, sampleRate, "c", tags);
  }

  /**
   * Sets the specified gauge to a given value.
   *
   * @param name the name of the gauge to set
   * @param value the value to set the gauge to
   */
  public void gauge(String name, long value) {
    gauge(name, value, null);
  }

  /**
   * Sets the specified gauge to a given value.
   *
   * @param name the name of the gauge to set
   * @param value the value to set the gauge to
   * @param tags Only for DogStatsD compatible collectors
   */
  public void gauge(String name, long value, String tags) {
    qManager.submit(name, value, 0d, "g", tags);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   */
  public void time(String name, long value) {
    time(name, value, null);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   * @param sampleRate the sampling rate being employed
   */
  public void time(String name, long value, double sampleRate) {
    time(name, value, sampleRate, null);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   * @param tags Only for DogStatsD compatible collectors
   */
  public void time(String name, long value, String tags) {
    time(name, value, 0d, tags);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   * @param sampleRate the sampling rate being employed
   * @param tags Only for DogStatsD compatible collectors
   */
  public void time(String name, long value, double sampleRate, String tags) {
    qManager.submit(name, value, sampleRate, "ms", tags);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   */
  public void histo(String name, long value) {
    histo(name, value, null);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   * @param sampleRate the sampling rate being employed
   */
  public void histo(String name, long value, double sampleRate) {
    histo(name, value, sampleRate, null);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   * @param tags Only for DogStatsD compatible collectors
   */
  public void histo(String name, long value, String tags) {
    histo(name, value, 0d, tags);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   * @param sampleRate the sampling rate being employed
   * @param tags Only for DogStatsD compatible collectors
   */
  public void histo(String name, long value, double sampleRate, String tags) {
    qManager.submit(name, value, sampleRate, "h", tags);
  }

  /**
   * Records an occurrence of a unique event.
   *
   * @param name the name of the set
   * @param id the value to be added to the set
   * @param tags Only for DogStatsD compatible collectors
   */
  public void unique(String name, String id, String tags) {
    qManager.submit(name, id, "s", tags);
  }

  /**
   * Records an occurrence of a unique event.
   *
   * @param name the name of the set
   * @param id the value to be added to the set
   */
  public void unique(String name, String id) {
    unique(name, id, null);
  }

  /**
   * Sends an event to a DogStatsD compatible collector
   *
   * @param title event name
   * @param text event text
   */
  public void event(String title, String text) {
    qManager.event(title, text, 0, null, null, null, null, null, null);
  }

  /**
   * Sends an event to a DogStatsD compatible collector
   *
   * @param title The event name
   * @param text The event text
   * @param tags Assigned comma delimited tags
   */
  public void event(String title, String text, String tags) {
    qManager.event(title, text, 0, null, null, null, null, null, tags);
  }

  /**
   * Sends an event to a DogStatsD compatible collector
   *
   * @param title event name
   * @param text event text
   * @param timestamp Assign a timestamp to the event. 0 means the current date
   * @param host Assign a hostname to the event. May be null
   * @param group Assign an aggregation key to the event. May be null
   * @param sourceType Assign a source type to the event. May be null
   * @param priority priority - may be null for NORMAL
   * @param alertType alert type - may be null for INFO
   * @param tags Assigned comma delimited tags
   */
  public void event(
      String title,
      String text,
      long timestamp,
      String host,
      String group,
      String sourceType,
      Statsd.Priority priority,
      Statsd.AlertType alertType,
      String tags) {
    qManager.event(title, text, timestamp, host, group, sourceType, priority, alertType, tags);
  }
}
