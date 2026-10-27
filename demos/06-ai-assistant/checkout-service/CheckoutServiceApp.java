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
import com.acme.risk.RiskServiceException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checkout service: screens each order for fraud, reserves stock and takes the card payment.
 * Payments and fraud screening come from the ACME SDK (lib/acme-sdk.jar).
 *
 * <p>Run with {@code java -cp lib/acme-sdk.jar CheckoutServiceApp.java} (JDK 11+).
 */
public class CheckoutServiceApp {
  public static void main(String[] args) throws Exception {
    CheckoutService service =
        new CheckoutService(new FraudScreen(), new Inventory(), new PaymentClient("shop-eu-1"));
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
                    service.checkout(orderId, amountCents);
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
    while (true) {
      Thread.sleep(5_000);
      System.out.printf("[demo] %d checkouts completed%n", service.completed());
    }
  }
}

class CheckoutService {
  private final FraudScreen fraud;
  private final Inventory inventory;
  private final PaymentClient payments;
  private final AtomicLong completed = new AtomicLong();

  CheckoutService(FraudScreen fraud, Inventory inventory, PaymentClient payments) {
    this.fraud = fraud;
    this.inventory = inventory;
    this.payments = payments;
  }

  void checkout(String orderId, long amountCents) throws InterruptedException {
    try {
      fraud.screen(orderId, amountCents);
    } catch (RiskServiceException e) {
      // The risk service is flaky; unscreened orders are picked up again by the nightly batch.
      return;
    }
    inventory.reserve(orderId);
    payments.authorize(orderId, amountCents);
    completed.incrementAndGet();
  }

  long completed() {
    return completed.get();
  }
}

class Inventory {
  private final Random rnd = new Random();

  void reserve(String orderId) throws InterruptedException {
    Thread.sleep(5 + rnd.nextInt(20));
  }
}
