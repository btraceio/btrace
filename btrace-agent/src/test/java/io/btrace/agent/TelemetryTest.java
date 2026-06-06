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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TelemetryTest {

  @AfterEach
  void clearProp() {
    System.clearProperty("btrace.telemetry");
  }

  @Test
  void enabledByDefault() {
    System.clearProperty("btrace.telemetry");
    assertTrue(Telemetry.isEnabled());
  }

  @Test
  void disabledWhenPropertyIsFalse() {
    System.setProperty("btrace.telemetry", "false");
    assertFalse(Telemetry.isEnabled());
  }

  @Test
  void enabledWhenPropertyIsTrue() {
    System.setProperty("btrace.telemetry", "true");
    assertTrue(Telemetry.isEnabled());
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
  void fireAsyncDoesNotThrowWhenDisabled() {
    System.setProperty("btrace.telemetry", "false");
    assertDoesNotThrow(() -> Telemetry.fireAsync("2.2.6", "premain"));
  }

  @Test
  void fireAsyncReturnsImmediately() throws InterruptedException {
    System.setProperty("btrace.telemetry", "true");
    long start = System.currentTimeMillis();
    assertDoesNotThrow(() -> Telemetry.fireAsync("2.2.6", "agentmain"));
    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed < 100, "fireAsync blocked for " + elapsed + "ms");
    // Let daemon threads settle before the next test
    Thread.sleep(300);
  }
}
