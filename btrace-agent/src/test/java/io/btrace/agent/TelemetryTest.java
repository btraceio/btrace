/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelemetryTest {

  @BeforeEach
  void resetProp() {
    System.clearProperty("btrace.telemetry");
  }

  @AfterEach
  void clearProp() {
    System.clearProperty("btrace.telemetry");
  }

  @Test
  void disabledByDefault() {
    System.clearProperty("btrace.telemetry");
    assertFalse(Telemetry.isEnabled(null));
  }

  @Test
  void enabledWhenAgentArgumentIsTrue() {
    System.setProperty("btrace.telemetry", "false");
    assertTrue(Telemetry.isEnabled("true"));
  }

  @Test
  void agentArgumentFalseOverridesSystemProperty() {
    System.setProperty("btrace.telemetry", "true");
    AtomicInteger attempts = new AtomicInteger();

    boolean scheduled =
        Telemetry.fireAsync("false", "2.2.6", "premain", payload -> attempts.incrementAndGet());

    assertFalse(scheduled);
    assertEquals(0, attempts.get());
  }

  @Test
  void systemPropertyRemainsAnExplicitOptIn() {
    System.setProperty("btrace.telemetry", "true");
    assertTrue(Telemetry.isEnabled(null));
  }

  @Test
  void nonBooleanValueDoesNotEnableTelemetry() {
    assertFalse(Telemetry.isEnabled("yes"));
  }

  @Test
  void payloadContainsRequiredFields() {
    String payload = Telemetry.buildPayload("2.2.6", "premain");
    assertTrue(payload.contains("\"event\":\"agent_start\""));
    assertTrue(payload.contains("\"java_version\":\""));
    assertTrue(payload.contains("\"os_name\":\""));
    assertTrue(payload.contains("\"btrace_version\":\"2.2.6\""));
    assertTrue(payload.contains("\"agent_mode\":\"premain\""));
    assertTrue(payload.contains("\"distinct_id\":\""));
  }

  @Test
  void payloadEscapesSpecialCharacters() {
    String payload = Telemetry.buildPayload("2.2.6-\"evil\"", "premain\\x");
    assertTrue(payload.contains("2.2.6-\\\"evil\\\""));
    assertTrue(payload.contains("premain\\\\x"));
  }

  @Test
  void defaultPathDoesNotInvokeTransport() {
    AtomicInteger attempts = new AtomicInteger();

    boolean scheduled =
        Telemetry.fireAsync(null, "2.2.6", "premain", payload -> attempts.incrementAndGet());

    assertFalse(scheduled);
    assertEquals(0, attempts.get());
  }

  @Test
  void optInAttemptsExactlyOneEvent() throws InterruptedException {
    AtomicInteger attempts = new AtomicInteger();
    CountDownLatch attempted = new CountDownLatch(1);

    boolean scheduled =
        Telemetry.fireAsync(
            "true",
            "2.2.6",
            "agentmain",
            payload -> {
              attempts.incrementAndGet();
              attempted.countDown();
            });

    assertTrue(scheduled);
    assertTrue(attempted.await(5, TimeUnit.SECONDS));
    assertEquals(1, attempts.get());
  }

  @Test
  void transportFailureIsIgnored() {
    Telemetry.Transport unavailableEndpoint =
        payload -> {
          throw new IllegalStateException("endpoint unavailable");
        };

    assertDoesNotThrow(() -> Telemetry.deliverSafely(unavailableEndpoint, "{}"));
  }
}
