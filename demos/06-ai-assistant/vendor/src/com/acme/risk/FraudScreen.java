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
package com.acme.risk;

import java.util.Random;

/**
 * Stand-in for a third-party fraud-screening SDK, shipped to the checkout service as a compiled JAR
 * only. Orders of 500.00 or more go through deep scoring, which times out about half the time. The
 * SDK fails closed and quietly: a timed-out score becomes a {@link Verdict#HOLD}, exactly like a
 * suspicious order, and nothing is logged. From the caller's side, a hold is a hold.
 */
public final class FraudScreen {
  private static final long DEEP_SCORING_CENTS = 50_000;

  private final Random rnd = new Random();

  /** Screens an order. */
  public Verdict screen(String orderId, long amountCents) throws InterruptedException {
    Thread.sleep(3 + rnd.nextInt(7));
    if (amountCents >= DEEP_SCORING_CENTS) {
      try {
        deepScore(orderId);
      } catch (RiskServiceException e) {
        return Verdict.HOLD;
      }
    }
    return Verdict.APPROVE;
  }

  private void deepScore(String orderId) throws RiskServiceException, InterruptedException {
    Thread.sleep(10 + rnd.nextInt(20));
    if (rnd.nextBoolean()) {
      throw new RiskServiceException("deep scoring timed out for order " + orderId);
    }
  }
}
