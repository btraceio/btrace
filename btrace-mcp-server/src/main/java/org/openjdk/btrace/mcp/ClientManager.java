/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.mcp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.btrace.client.Client;
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
