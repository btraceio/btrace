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
package io.btrace.core.comm;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects a connection that went silent without being closed: a dropped network path, a suspended
 * host, or a peer that is alive but no longer reading. A blocking socket read never returns in
 * those cases, so the monitor runs on its own daemon thread and calls {@code onTimeout} once
 * nothing has been {@linkplain #received() received} for the timeout. {@code onTimeout} is expected
 * to close the socket, which makes the blocked read fail and lets the existing lost-connection
 * handling run.
 *
 * <p>With a {@link Pinger}, the monitor also sends a {@link HeartbeatCommand} every interval, so
 * the peer always has something to answer and to observe. Pings run on a separate thread, so a ping
 * stuck on a full send buffer cannot delay the timeout.
 *
 * <p>Heartbeats are a v2 protocol feature; see {@link Command#HEARTBEAT}.
 */
public final class HeartbeatMonitor {
  /** Milliseconds between client heartbeats; 0 or less disables heartbeats. */
  public static final String INTERVAL_PROPERTY = "btrace.heartbeat.interval";

  /**
   * Milliseconds without receiving anything after which a connection is closed; 0 or less disables
   * the check.
   */
  public static final String TIMEOUT_PROPERTY = "btrace.heartbeat.timeout";

  public static final long DEFAULT_INTERVAL_MILLIS = 10_000L;
  public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

  private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);
  private static final long MIN_TICK_MILLIS = 10L;
  private static final long MAX_TICK_MILLIS = 1_000L;

  /** Sends one heartbeat to the peer. */
  public interface Pinger {
    void ping(long nonce) throws IOException;
  }

  private final long intervalNanos;
  private final long timeoutNanos;
  private final long tickMillis;
  private final Pinger pinger;
  private final Runnable onTimeout;
  private final Thread thread;
  private final ExecutorService pingExecutor;

  private volatile long lastReceived = System.nanoTime();
  private volatile boolean closed;
  private volatile boolean timedOut;

  private HeartbeatMonitor(
      String name, long intervalMillis, long timeoutMillis, Pinger pinger, Runnable onTimeout) {
    this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
    this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    long period = pinger != null ? Math.min(intervalMillis, timeoutMillis) : timeoutMillis;
    this.tickMillis = Math.max(MIN_TICK_MILLIS, Math.min(MAX_TICK_MILLIS, period / 4));
    this.pinger = pinger;
    this.onTimeout = onTimeout;
    this.pingExecutor =
        pinger != null
            ? Executors.newSingleThreadExecutor(
                r -> {
                  Thread t = new Thread(r, name + " ping");
                  t.setDaemon(true);
                  return t;
                })
            : null;
    this.thread = new Thread(this::run, name);
    this.thread.setDaemon(true);
  }

  /**
   * Starts a monitor that only checks for silence; the peer is expected to send heartbeats.
   *
   * @return the running monitor, or {@code null} if {@code timeoutMillis} disables it
   */
  public static HeartbeatMonitor watch(String name, long timeoutMillis, Runnable onTimeout) {
    if (timeoutMillis <= 0) {
      return null;
    }
    HeartbeatMonitor monitor = new HeartbeatMonitor(name, 0, timeoutMillis, null, onTimeout);
    monitor.thread.start();
    return monitor;
  }

  /**
   * Starts a monitor that sends a heartbeat every interval and checks for silence.
   *
   * @return the running monitor, or {@code null} if either value disables heartbeats
   */
  public static HeartbeatMonitor pingAndWatch(
      String name, long intervalMillis, long timeoutMillis, Pinger pinger, Runnable onTimeout) {
    if (intervalMillis <= 0 || timeoutMillis <= 0) {
      return null;
    }
    HeartbeatMonitor monitor =
        new HeartbeatMonitor(name, intervalMillis, timeoutMillis, pinger, onTimeout);
    monitor.thread.start();
    return monitor;
  }

  /** {@value #INTERVAL_PROPERTY}, or its default when unset or not a number. */
  public static long intervalMillis() {
    return longProperty(INTERVAL_PROPERTY, DEFAULT_INTERVAL_MILLIS);
  }

  /** {@value #TIMEOUT_PROPERTY}, or its default when unset or not a number. */
  public static long timeoutMillis() {
    return longProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_MILLIS);
  }

  private static long longProperty(String name, long defaultValue) {
    String value = System.getProperty(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      log.warn("Ignoring {}={}: not a number of milliseconds", name, value);
      return defaultValue;
    }
  }

  /** Records that something arrived from the peer. */
  public void received() {
    lastReceived = System.nanoTime();
  }

  /** {@code true} once the monitor closed the connection for being silent. */
  public boolean isTimedOut() {
    return timedOut;
  }

  /** Stops the monitor without calling {@code onTimeout}. */
  public void close() {
    closed = true;
    thread.interrupt();
    if (pingExecutor != null) {
      pingExecutor.shutdownNow();
    }
  }

  private void run() {
    long nonce = 0;
    // The first ping waits one interval: a session that ends at once (a refused probe, an exit
    // right after reconnecting) then never has a heartbeat in flight when the peer closes.
    long nextPing = System.nanoTime() + intervalNanos;
    Future<?> pendingPing = null;
    try {
      while (!closed) {
        long now = System.nanoTime();
        if (now - lastReceived >= timeoutNanos) {
          timedOut = true;
          closed = true;
          onTimeout.run();
          return;
        }
        if (pinger != null
            && now - nextPing >= 0
            && (pendingPing == null || pendingPing.isDone())) {
          long n = nonce++;
          pendingPing =
              pingExecutor.submit(
                  () -> {
                    try {
                      pinger.ping(n);
                    } catch (IOException e) {
                      // The read side sees the broken connection too; nothing to do here.
                      log.debug("Unable to send heartbeat: {}", e.toString());
                    }
                  });
          nextPing = now + intervalNanos;
        }
        Thread.sleep(tickMillis);
      }
    } catch (InterruptedException e) {
      // close() was called
    } finally {
      if (pingExecutor != null) {
        pingExecutor.shutdownNow();
      }
    }
  }
}
