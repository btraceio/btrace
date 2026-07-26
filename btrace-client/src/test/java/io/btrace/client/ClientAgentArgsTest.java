/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.btrace.core.Args;
import io.btrace.core.ArgsMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The agent argument string the client hands to {@code VirtualMachine#loadAgent} must survive the
 * agent's own parsing.
 *
 * <p>{@link ArgsMap} splits each comma-separated entry on {@code =} and dispatches on the key, so a
 * malformed entry is not an error - it parses into a key nothing matches and the setting is dropped
 * in silence. {@code cmdQueueLimit} was shipped that way, fixed, then reverted back by an unrelated
 * commit, because nothing asserted the wire shape. These tests assert it.
 */
class ClientAgentArgsTest {

  @Test
  @DisplayName("the first entry carries no leading separator")
  void firstEntryHasNoLeadingComma() {
    assertEquals("port=2020", Client.appendAgentArg("", Args.PORT, 2020));
  }

  @Test
  @DisplayName("subsequent entries are comma separated")
  void subsequentEntriesAreCommaSeparated() {
    String args = Client.appendAgentArg("", Args.PORT, 2020);
    args = Client.appendAgentArg(args, Args.DEBUG, "true");

    assertEquals("port=2020,debug=true", args);
  }

  @Test
  @DisplayName("cmdQueueLimit round-trips through the agent's parser")
  void cmdQueueLimitRoundTripsThroughArgsMap() {
    String args = Client.appendAgentArg("", Args.PORT, 2020);
    args = Client.appendAgentArg(args, Args.CMD_QUEUE_LIMIT, "512");

    ArgsMap parsed = new ArgsMap(args.split(","));

    assertEquals(
        "512",
        parsed.get(Args.CMD_QUEUE_LIMIT),
        "the agent must see cmdQueueLimit as a key, not as part of a value");
  }

  @Test
  @DisplayName("a fully populated argument string parses without an empty key")
  void noEntryParsesToAnEmptyKey() {
    String args = Client.appendAgentArg("", Args.PORT, 2020);
    args = Client.appendAgentArg(args, Args.STATSD, "localhost:8125");
    args = Client.appendAgentArg(args, Args.DEBUG, "true");
    args = Client.appendAgentArg(args, Args.TRUSTED, "true");
    args = Client.appendAgentArg(args, Args.DUMP_CLASSES, "true");
    args = Client.appendAgentArg(args, Args.DUMP_DIR, "/tmp/dump");
    args = Client.appendAgentArg(args, Args.TRACK_RETRANSFORMS, "true");
    args = Client.appendAgentArg(args, Args.CMD_QUEUE_LIMIT, "512");
    args = Client.appendAgentArg(args, Args.PROBE_DESC_PATH, ".");
    args = Client.appendAgentArg(args, "$btrace.system.appendJar", "/tmp/x.jar");

    ArgsMap parsed = new ArgsMap(args.split(","));

    for (Map.Entry<String, String> entry : parsed) {
      assertFalse(
          entry.getKey().isEmpty(),
          "an empty key means an entry was built with a misplaced '='; the agent silently drops it");
    }
    assertEquals("512", parsed.get(Args.CMD_QUEUE_LIMIT));
    assertEquals("localhost:8125", parsed.get(Args.STATSD));
    assertEquals("/tmp/x.jar", parsed.get("$btrace.system.appendJar"));
  }

  @Test
  @DisplayName("a malformed key is rejected rather than silently dropped downstream")
  void malformedKeysAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Client.appendAgentArg("", "", "v"));
    assertThrows(IllegalArgumentException.class, () -> Client.appendAgentArg("", null, "v"));
    assertThrows(IllegalArgumentException.class, () -> Client.appendAgentArg("", "=bad", "v"));
    assertThrows(IllegalArgumentException.class, () -> Client.appendAgentArg("", "ba,d", "v"));
  }

  /**
   * Pins the failure mode the guard exists for: the historical {@code ",=" + KEY + value} typo
   * parses into an empty key, which no {@code case} in the agent matches.
   */
  @Test
  @DisplayName("the historical malformed shape does parse to an empty key")
  void malformedShapeParsesToEmptyKey() {
    String malformed = "port=2020" + ",=" + Args.CMD_QUEUE_LIMIT + "512";

    ArgsMap parsed = new ArgsMap(malformed.split(","));

    assertEquals(null, parsed.get(Args.CMD_QUEUE_LIMIT), "the setting is lost, not reported");
    assertEquals(Args.CMD_QUEUE_LIMIT + "512", parsed.get(""), "it lands under an empty key");
  }
}
