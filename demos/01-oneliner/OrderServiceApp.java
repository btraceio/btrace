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

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Target application for demos/01-oneliner (and reused by demos 02, 03, 06 and 07): a tiny order
 * service with two defects baked in on purpose. Run with {@code java OrderServiceApp.java}
 * (JDK 11+, no dependencies).
 *
 * <ul>
 *   <li>{@code OrderService.chargeCard} hits a "slow payment provider" for roughly 10% of calls
 *       (250-400 ms instead of 10-50 ms): find it with a {@code @return if duration>200ms} probe.
 *   <li>{@code OrderService.validateOrder} throws for roughly 8% of orders and the app swallows
 *       the exception: find it with an {@code @error} probe.
 * </ul>
 */
public class OrderServiceApp {
  public static void main(String[] args) throws Exception {
    OrderService service = new OrderService();
    AtomicLong processed = new AtomicLong();
    AtomicLong failed = new AtomicLong();

    for (int i = 0; i < 3; i++) {
      Thread worker =
          new Thread(
              () -> {
                Random rnd = new Random();
                while (true) {
                  String orderId = "order-" + rnd.nextInt(100_000);
                  try {
                    service.processOrder(orderId);
                    processed.incrementAndGet();
                  } catch (IllegalStateException e) {
                    failed.incrementAndGet();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    Thread.sleep(50 + rnd.nextInt(150));
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                }
              },
              "order-worker-" + i);
      worker.setDaemon(true);
      worker.start();
    }

    System.out.println("[demo] order service running - stop with Ctrl+C");
    while (true) {
      Thread.sleep(5_000);
      System.out.printf(
          "[demo] processed %d orders, %d failed%n", processed.get(), failed.get());
    }
  }
}

class OrderService {
  private final Random rnd = new Random();

  void processOrder(String orderId) throws InterruptedException {
    validateOrder(orderId);
    lookupInventory(orderId);
    chargeCard(orderId);
  }

  /** Fails for roughly 8% of orders - find it with an @error probe. */
  void validateOrder(String orderId) throws InterruptedException {
    if (rnd.nextInt(100) < 8) {
      throw new IllegalStateException("order failed validation: " + orderId);
    }
    Thread.sleep(2 + rnd.nextInt(8));
  }

  void lookupInventory(String orderId) throws InterruptedException {
    Thread.sleep(5 + rnd.nextInt(35));
  }

  /** Usually fast, but roughly 10% of calls hit a "slow payment provider" - find it with BTrace. */
  void chargeCard(String orderId) throws InterruptedException {
    if (rnd.nextInt(100) < 10) {
      Thread.sleep(250 + rnd.nextInt(150));
    } else {
      Thread.sleep(10 + rnd.nextInt(40));
    }
  }
}
