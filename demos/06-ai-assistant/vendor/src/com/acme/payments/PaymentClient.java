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
package com.acme.payments;

import java.util.Random;

/**
 * Stand-in for a third-party payment SDK. It ships to the demo's checkout service as a compiled JAR
 * only, the way vendor libraries usually do, so the slow path below can be found by probing the
 * running JVM but not by reading the service's own source.
 *
 * <p>Every authorization needs a gateway session. Sessions expire after a handful of calls, and
 * minting a new one (handshake plus token exchange) takes 300-450 ms while holding the client's
 * lock, so every checkout that arrives meanwhile waits too.
 */
public final class PaymentClient {
  private static final int CALLS_PER_SESSION = 12;

  private final String merchantId;
  private final Random rnd = new Random();
  private int callsLeft;
  private long sessionId;

  public PaymentClient(String merchantId) {
    this.merchantId = merchantId;
  }

  /** Authorizes a card payment and returns the gateway's authorization code. */
  public String authorize(String orderId, long amountCents) throws InterruptedException {
    long session = currentSession();
    Thread.sleep(15 + rnd.nextInt(30));
    return merchantId + "-" + session + "-" + orderId;
  }

  private synchronized long currentSession() throws InterruptedException {
    if (callsLeft == 0) {
      sessionId = refreshSession();
      callsLeft = CALLS_PER_SESSION;
    }
    callsLeft--;
    return sessionId;
  }

  private long refreshSession() throws InterruptedException {
    Thread.sleep(300 + rnd.nextInt(150));
    return sessionId + 1;
  }
}
