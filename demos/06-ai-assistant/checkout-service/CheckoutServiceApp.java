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

import com.acme.payments.PaymentClient;
import com.acme.risk.FraudScreen;
import com.acme.risk.Verdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checkout service: screens each order for fraud, reserves stock and takes the card payment.
 * Payments and fraud screening come from the ACME SDK (lib/acme-sdk.jar). Orders the fraud screen
 * holds go to the manual review queue.
 *
 * <p>Run with {@code java -cp lib/acme-sdk.jar CheckoutServiceApp.java} (JDK 11+).
 */
public class CheckoutServiceApp {
  public static void main(String[] args) throws Exception {
    Metrics metrics = new Metrics();
    CheckoutService service =
        new CheckoutService(
            new FraudScreen(), new Inventory(), new PaymentClient("shop-eu-1"), new ReviewQueue());
    for (int i = 0; i < 4; i++) {
      Thread worker =
          new Thread(
              () -> {
                Random rnd = new Random();
                long seq = 0;
                while (true) {
                  String orderId = Thread.currentThread().getName() + "-" + (++seq);
                  long amountCents = 500 + rnd.nextInt(90_000);
                  try {
                    long start = System.nanoTime();
                    boolean completed = service.checkout(orderId, amountCents);
                    metrics.record(completed, System.nanoTime() - start);
                    Thread.sleep(20 + rnd.nextInt(60));
                  } catch (InterruptedException e) {
                    return;
                  }
                }
              },
              "checkout-" + i);
      worker.setDaemon(true);
      worker.start();
    }
    System.out.println("[demo] checkout service running - stop with Ctrl+C");
    for (int second = 1; ; second++) {
      Thread.sleep(1_000);
      metrics.report();
      if (second % 5 == 0) {
        System.out.printf("[demo] %d checkouts completed%n", service.completed());
      }
    }
  }
}

class CheckoutService {
  private final FraudScreen fraud;
  private final Inventory inventory;
  private final PaymentClient payments;
  private final ReviewQueue review;
  private final AtomicLong completed = new AtomicLong();

  CheckoutService(FraudScreen fraud, Inventory inventory, PaymentClient payments, ReviewQueue review) {
    this.fraud = fraud;
    this.inventory = inventory;
    this.payments = payments;
    this.review = review;
  }

  /** Returns whether the order completed; held orders wait in the manual review queue. */
  boolean checkout(String orderId, long amountCents) throws InterruptedException {
    if (fraud.screen(orderId, amountCents) == Verdict.HOLD) {
      review.add(orderId);
      return false;
    }
    inventory.reserve(orderId);
    payments.authorize(orderId, amountCents);
    completed.incrementAndGet();
    return true;
  }

  long completed() {
    return completed.get();
  }
}

/** Orders the fraud screen held, waiting for a person to look at them. */
class ReviewQueue {
  private final List<String> pending = Collections.synchronizedList(new ArrayList<>());

  void add(String orderId) {
    pending.add(orderId);
  }
}

class Inventory {
  private final Random rnd = new Random();

  void reserve(String orderId) throws InterruptedException {
    Thread.sleep(5 + rnd.nextInt(20));
  }
}

/** Per-second throughput and latency, printed as one line for the dashboard scraper. */
class Metrics {
  private final List<Long> latencies = new ArrayList<>();
  private long completed;
  private long held;

  synchronized void record(boolean done, long nanos) {
    latencies.add(nanos);
    if (done) {
      completed++;
    } else {
      held++;
    }
  }

  void report() {
    List<Long> window;
    long done;
    long hold;
    synchronized (this) {
      window = new ArrayList<>(latencies);
      done = completed;
      hold = held;
      latencies.clear();
      completed = 0;
      held = 0;
    }
    Collections.sort(window);
    long p50 = window.isEmpty() ? 0 : window.get(window.size() / 2) / 1_000_000;
    long p99 = window.isEmpty() ? 0 : window.get((int) Math.min(window.size() - 1, Math.ceil(window.size() * 0.99) - 1)) / 1_000_000;
    System.out.printf(
        "[metrics] ts=%d completed=%d held=%d p50=%d p99=%d%n",
        System.currentTimeMillis(), done, hold, p50, p99);
  }
}
