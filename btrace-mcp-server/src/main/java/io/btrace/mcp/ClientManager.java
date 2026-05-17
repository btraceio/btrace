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
package io.btrace.mcp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.btrace.client.Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages BTrace client instances per JVM. Tracks active sessions so that tools like send_event,
 * detach_probe, and exit_probe can interact with already-deployed probes.
 */
public final class ClientManager {
  private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

  /** Key is "pid:port", value is the active Client instance. */
  private static final Map<String, Client> activeClients = new ConcurrentHashMap<>();

  private ClientManager() {}

  /** Creates a new BTrace Client for the given port. Does not reuse existing sessions. */
  public static Client getClient(int port) {
    return new Client(port);
  }

  /** Registers a client as active for a given PID and port. */
  public static void registerClient(String pid, int port, Client client) {
    String key = pid + ":" + port;
    activeClients.put(key, client);
    log.debug("Registered client for {}", key);
  }

  /** Returns an existing active client for the given PID and port, or null if none. */
  public static Client getExistingClient(String pid, int port) {
    String key = pid + ":" + port;
    return activeClients.get(key);
  }

  /** Removes and returns an active client for the given PID and port. */
  public static Client removeClient(String pid, int port) {
    String key = pid + ":" + port;
    Client removed = activeClients.remove(key);
    if (removed != null) {
      log.debug("Removed client for {}", key);
    }
    return removed;
  }

  /** Closes all active client sessions. Called on shutdown. */
  public static void closeAll() {
    for (Map.Entry<String, Client> entry : activeClients.entrySet()) {
      try {
        entry.getValue().close();
        log.debug("Closed client for {}", entry.getKey());
      } catch (Exception e) {
        log.warn("Error closing client for {}", entry.getKey(), e);
      }
    }
    activeClients.clear();
  }
}
