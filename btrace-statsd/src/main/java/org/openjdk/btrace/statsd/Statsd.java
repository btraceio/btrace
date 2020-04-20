/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ThreadFactory;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.services.spi.SimpleService;

/**
 * A simple way to submit <a href="https://github.com/etsy/statsd/">statsd</a> metrics.
 *
 * <p>Use the following code to obtain an instance:
 *
 * <pre>
 * <code>
 * {@literal @}Injected(factoryMethod = "getInstance")
 *   private static Statsd s;
 * </code>
 * </pre>
 *
 * @author Jaroslav Bachorik
 */
public final class Statsd extends SimpleService {
  private static final Charset CHARSET = StandardCharsets.US_ASCII;
  private final QManager qManager = new QManager();
  private final ExecutorService e =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "jStatsD Client Submitter");
              t.setDaemon(true);
              return t;
            }
          });

  @SuppressWarnings("FutureReturnValueIgnored")
  private Statsd() {
    e.submit(
        new Runnable() {
          @Override
          public void run() {
            DatagramSocket ds = null;
            boolean entered = BTraceRuntime.enter();
            try {
              ds = new DatagramSocket();
              DatagramPacket dp = new DatagramPacket(new byte[0], 0);
              try {
                dp.setAddress(InetAddress.getByName(SharedSettings.GLOBAL.getStatsdHost()));
              } catch (UnknownHostException e) {
                System.err.println(
                    "[statsd] invalid host defined: " + SharedSettings.GLOBAL.getStatsdHost());
                dp.setAddress(InetAddress.getLoopbackAddress());
              } catch (SecurityException e) {
                dp.setAddress(InetAddress.getLoopbackAddress());
              }
              dp.setPort(SharedSettings.GLOBAL.getStatsdPort());

              while (true) {
                Collection<String> msgs = new ArrayList<>();
                msgs.add(qManager.getQ().take());
                qManager.getQ().drainTo(msgs);

                StringBuilder sb = new StringBuilder();
                for (String m : msgs) {
                  if (sb.length() + m.length() < 511) {
                    sb.append(m).append('\n');
                  } else {
                    dp.setData(sb.toString().getBytes(CHARSET));
                    ds.send(dp);
                    sb.setLength(0);
                  }
                }
                if (sb.length() > 0) {
                  dp.setData(sb.toString().getBytes(CHARSET));
                  ds.send(dp);
                }
              }
            } catch (IOException | InterruptedException e) {
              e.printStackTrace();
            } finally {
              if (entered) {
                BTraceRuntime.leave();
              }
            }
          }
        });
  }

  public static Statsd getInstance() {
    return Singleton.INSTANCE;
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
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void increment(String name, String tags) {
    qManager.delta(name, 1, 0.0d, tags);
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   */
  public void increment(String name, double sampleRate) {
    qManager.delta(name, 1, sampleRate, null);
  }

  /**
   * Increase the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
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
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void decrement(String name, String tags) {
    qManager.delta(name, -1, 0.0d, tags);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   */
  public void decrement(String name, double sampleRate) {
    qManager.delta(name, -1, sampleRate, null);
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
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
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void count(String name, long count, String tags) {
    count(name, count, 0d, tags);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   */
  public void count(String name, long count, double sampleRate) {
    count(name, count, sampleRate, null);
  }

  /**
   * Adjusts the specified counter by a given delta.
   *
   * @param name the name of the counter to adjust
   * @param count the counter value
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this counter is being sent sampled every 1/10th of the time.
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
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
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
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
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this metric timing is being sent sampled every 1/10th of the time.
   */
  public void time(String name, long value, double sampleRate) {
    time(name, value, sampleRate, null);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void time(String name, long value, String tags) {
    time(name, value, 0d, tags);
  }

  /**
   * Records the timing information for the specified metric.
   *
   * @param name the metric name
   * @param value the measured time
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this metric timing is being sent sampled every 1/10th of the time.
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
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
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this metric value is being sent sampled every 1/10th of the time.
   */
  public void histo(String name, long value, double sampleRate) {
    histo(name, value, sampleRate, null);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void histo(String name, long value, String tags) {
    histo(name, value, 0d, tags);
  }

  /**
   * Adds a value to the named histogram.
   *
   * @param name the histogram name
   * @param value the measured value
   * @param sampleRate the sampling rate being employed. For example, a rate of 0.1 would tell
   *     StatsD that this metric value is being sent sampled every 1/10th of the time.
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void histo(String name, long value, double sampleRate, String tags) {
    qManager.submit(name, value, sampleRate, "h", tags);
  }

  /**
   * StatsD supports counting unique occurrences of events between flushes. Call this method to
   * records an occurrence of the specified named event.
   *
   * @param name the name of the set
   * @param id the value to be added to the set
   * @param tags Only for DogStatsD compatible collectors. Assigned comma delimited tags. A tag
   *     value is delimited by colon.
   */
  public void unique(String name, String id, String tags) {
    qManager.submit(name, id, "s", tags);
  }

  /**
   * StatsD supports counting unique occurrences of events between flushes. Call this method to
   * records an occurrence of the specified named event.
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
   * @param tags Assigned comma delimited tags. A tag value is delimited by colon.
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
   * @param group Assign an aggregation key to the event, to group it with some others. May be null
   * @param sourceType Assign a source type to the event. May be null
   * @param priority {@linkplain Priority} - may be null for NORMAL
   * @param alertType {@linkplain AlertType} - may be null for INFO
   * @param tags Assigned comma delimited tags. A tag value is delimited by colon.
   */
  public void event(
      String title,
      String text,
      long timestamp,
      String host,
      String group,
      String sourceType,
      Priority priority,
      AlertType alertType,
      String tags) {

    qManager.event(title, text, timestamp, host, group, sourceType, priority, alertType, tags);
  }

  public enum Priority {
    NORMAL,
    LOW
  }

  public enum AlertType {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
  }

  private static final class Singleton {
    private static final Statsd INSTANCE = new Statsd();
  }
}
