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
package io.btrace.agent;

import io.btrace.instr.ProbeBudgets;
import io.btrace.runtime.IndyDispatcher;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces probe budgets: once per tick, removes every live probe that has outlived {@code
 * probeTtl} or whose hit rate since the previous tick exceeded {@code probeMaxRate}. A trip
 * disables the probe's handlers on the thread calling {@link #tick()}; the probe's exit, which runs
 * its {@code @OnExit} handlers and retransforms classes, runs on a separate exit thread so a slow
 * exit cannot hold up enforcement for the other probes. None of it runs on an application thread.
 */
final class BudgetGovernor {
  private static final Logger log = LoggerFactory.getLogger(BudgetGovernor.class);
  private static final long TICK_MILLIS = 1000L;
  // A tripped probe still installed this long after its exit returned is removed by force.
  private static final long FORCE_AFTER_NANOS = TimeUnit.SECONDS.toNanos(5);
  // ... and this long after the trip even if its exit never returns (a blocked @OnExit handler).
  private static final long FORCE_HARD_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(30);

  /** A probe's counter value at the tick it was taken. */
  private static final class Sample {
    long sum;
    long nanos;

    Sample(long sum, long nanos) {
      this.sum = sum;
      this.nanos = nanos;
    }
  }

  /** A probe this governor tripped and has not yet seen removed. */
  private static final class Trip {
    final String name;
    final long trippedAtNanos;
    // Written by the exit thread once budgetExit returned or threw.
    volatile boolean exitReturned;
    boolean forced;

    Trip(String name, long trippedAtNanos) {
      this.name = name;
      this.trippedAtNanos = trippedAtNanos;
    }
  }

  private final BudgetLimits limits;
  private final LongSupplier nanoTime;
  private final Supplier<Collection<Client>> clients;
  private final Executor exitExecutor;
  // Used only by the thread calling tick().
  private final Map<Client, Sample> samples = new IdentityHashMap<>();
  private final Map<Client, Trip> trips = new IdentityHashMap<>();

  BudgetGovernor(
      BudgetLimits limits,
      LongSupplier nanoTime,
      Supplier<Collection<Client>> clients,
      Executor exitExecutor) {
    this.limits = limits;
    this.nanoTime = nanoTime;
    this.clients = clients;
    this.exitExecutor = exitExecutor;
  }

  /** Starts the governor thread; does nothing when no limit is set. */
  static void start(BudgetLimits limits) {
    if (!limits.any()) {
      return;
    }
    // Anonymous classes, not lambdas: this runs on the premain path, which must not reach any
    // invokedynamic call site.
    final BudgetGovernor governor =
        new BudgetGovernor(
            limits,
            new LongSupplier() {
              @Override
              public long getAsLong() {
                return System.nanoTime();
              }
            },
            new Supplier<Collection<Client>>() {
              @Override
              public Collection<Client> get() {
                return Client.liveClients();
              }
            },
            new Executor() {
              @Override
              public void execute(Runnable task) {
                Thread exit = new Thread(task, "BTrace Budget Exit");
                exit.setDaemon(true);
                exit.start();
              }
            });
    Thread thread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (governor.runOnce()) {
                  // runOnce() never asks to stop; enforcement lasts as long as the JVM.
                }
              }
            },
            "BTrace Budget Governor");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Waits one tick, then checks every probe. Always returns {@code true}: neither an interrupt nor
   * a failure ends enforcement.
   */
  boolean runOnce() {
    try {
      Thread.sleep(TICK_MILLIS);
    } catch (InterruptedException e) {
      // Deliberately not restored: a set interrupt flag would make every later sleep return at
      // once and spin this loop.
      log.debug("Probe budget governor interrupted; it keeps running");
    }
    try {
      tick();
    } catch (Throwable t) {
      log.error("Probe budget check failed", t);
    }
    return true;
  }

  /** One pass over the live probes. Never throws; a failing probe does not stop the others. */
  void tick() {
    Collection<Client> current;
    try {
      current = clients.get();
      samples.keySet().retainAll(current);
      dropFinishedTrips(current);
    } catch (Throwable t) {
      log.error("Probe budget check failed", t);
      return;
    }
    for (Client client : current) {
      try {
        Trip trip = trips.get(client);
        if (trip != null) {
          followUp(client, trip);
        } else {
          check(client);
        }
      } catch (Throwable t) {
        log.error("Probe budget check failed for client {}", client.id, t);
      }
    }
  }

  /**
   * Forgets the trips of clients that are gone. A trip racing the probe's own exit can mark the
   * probe tripped after its unregistration cleared that mark; nothing else would remove it.
   */
  private void dropFinishedTrips(Collection<Client> current) {
    Iterator<Map.Entry<Client, Trip>> it = trips.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Client, Trip> entry = it.next();
      if (current.contains(entry.getKey())) {
        continue;
      }
      // Read before removing: an IdentityHashMap entry is no longer readable once removed.
      String name = entry.getValue().name;
      it.remove();
      boolean stillTripped = false;
      for (Trip other : trips.values()) {
        if (other.name.equals(name)) {
          stillTripped = true;
          break;
        }
      }
      if (!stillTripped) {
        ProbeBudgets.clearTripped(name);
      }
    }
  }

  private void check(Client client) {
    String name = client.probeInternalName();
    if (name == null
        || client.budgetExempt()
        || client.isShuttingDown()
        || ProbeBudgets.isTripped(name)) {
      return;
    }
    long ttl = limits.ttlSeconds();
    if (ttl > 0
        && nanoTime.getAsLong() - client.deployedAtNanos() > TimeUnit.SECONDS.toNanos(ttl)) {
      trip(client, name, "lifetime exceeded probeTtl=" + ttl + "s");
      return;
    }
    long maxRate = limits.maxRatePerSecond();
    if (maxRate <= 0) {
      return;
    }
    // Read the clock next to the counter: time spent on other probes earlier in this tick must
    // not count towards this probe's window.
    long now = nanoTime.getAsLong();
    // A non-creating read: the governor must not add state for a probe that is unregistering.
    long sum = ProbeBudgets.hits(name);
    Sample last = samples.get(client);
    if (last == null) {
      // The first tick that sees a probe only records the baseline.
      samples.put(client, new Sample(sum, now));
      return;
    }
    long elapsed = now - last.nanos;
    if (elapsed <= 0) {
      return;
    }
    double rate = (sum - last.sum) * 1e9 / elapsed;
    last.sum = sum;
    last.nanos = now;
    if (rate > maxRate) {
      trip(client, name, (long) rate + " hits/s exceeded probeMaxRate=" + maxRate);
    }
  }

  /**
   * A tripped probe is normally removed by its exit. When that exit did not remove it, because the
   * exit failed, the client stopped reading so the exit could not be delivered, or the client went
   * away and the runtime's exit was discarded, the probe is removed by force. A trip whose exit is
   * still running is left alone until the hard deadline, so a healthy client whose probe has slow
   * {@code @OnExit} handlers still gets its reason line and exit code.
   */
  private void followUp(final Client client, final Trip trip) {
    if (trip.forced || client.isShuttingDown()) {
      return;
    }
    long sinceTrip = nanoTime.getAsLong() - trip.trippedAtNanos;
    String why;
    if (trip.exitReturned && client.transportLost()) {
      why = "its client went away during the exit";
    } else if (trip.exitReturned && sinceTrip >= FORCE_AFTER_NANOS) {
      why = "its exit did not remove it";
    } else if (sinceTrip >= FORCE_HARD_DEADLINE_NANOS) {
      why = "its exit has not finished";
    } else {
      return;
    }
    log.warn(
        "Probe {} is still installed {} ms after its budget trip ({}); forcing its removal",
        trip.name,
        TimeUnit.NANOSECONDS.toMillis(sinceTrip),
        why);
    exitExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              client.forceBudgetRemoval();
            } catch (Throwable t) {
              log.error("Forced removal of probe {} failed", trip.name, t);
            }
          }
        });
    // Only once the removal has started: if it could not be started, the next tick tries again.
    trip.forced = true;
  }

  /**
   * Records the trip, disables the probe's handlers, so no handler runs while the probe exits, then
   * starts the exit on the exit executor. The trip is recorded first so that a failure later in
   * this method still leaves the probe to be followed up and removed.
   */
  private void trip(final Client client, final String name, String detail) {
    final Trip trip = new Trip(name, nanoTime.getAsLong());
    trips.put(client, trip);
    ProbeBudgets.markTripped(name);
    IndyDispatcher.invalidateProbe(name);
    final String reason = "probe " + client.declaredClassName() + " removed: " + detail;
    exitExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              client.budgetExit(reason);
            } catch (Throwable t) {
              log.error(
                  "Budget exit failed for probe {}; its handlers are disabled and its removal"
                      + " will be forced",
                  name,
                  t);
            } finally {
              trip.exitReturned = true;
            }
          }
        });
  }
}
