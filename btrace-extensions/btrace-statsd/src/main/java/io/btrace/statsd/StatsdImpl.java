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
package io.btrace.statsd;

import io.btrace.core.SharedSettings;
import io.btrace.core.extensions.Extension;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.extensions.ExtensionException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class StatsdImpl extends Extension implements Statsd {
  private static final class Endpoint {
    final InetAddress address;
    final int port;
    final DatagramSocket socket;

    Endpoint(InetAddress address, int port, DatagramSocket socket) {
      this.address = address;
      this.port = port;
      this.socket = socket;
    }
  }

  private final Object lifecycleLock = new Object();
  private volatile Endpoint endpoint;

  @Override
  public void initialize(ExtensionContext context) throws ExtensionException {
    super.initialize(context);
    Endpoint configured;
    try {
      InetAddress configuredAddress = InetAddress.getByName(SharedSettings.GLOBAL.getStatsdHost());
      int configuredPort = SharedSettings.GLOBAL.getStatsdPort();
      configured = new Endpoint(configuredAddress, configuredPort, new DatagramSocket());
    } catch (Throwable ignored) {
      configured = null;
    }
    swapEndpoint(configured);
  }

  @Override
  public void increment(String name) {
    increment(name, null);
  }

  @Override
  public void increment(String name, String tags) {
    Endpoint ep = endpoint;
    if (ep == null) {
      return;
    }
    try {
      StringBuilder sb = new StringBuilder();
      sb.append(name).append(":1|c");
      if (tags != null && !tags.isEmpty()) {
        sb.append("|#").append(tags);
      }
      byte[] data = sb.toString().getBytes(StandardCharsets.US_ASCII);
      DatagramPacket pkt = new DatagramPacket(data, data.length, ep.address, ep.port);
      ep.socket.send(pkt);
    } catch (Throwable ignore) {
      // Best-effort, ignore errors in script path
    }
  }

  @Override
  public void close() {
    swapEndpoint(null);
  }

  private void swapEndpoint(Endpoint next) {
    Endpoint previous;
    synchronized (lifecycleLock) {
      previous = endpoint;
      endpoint = next;
    }
    if (previous != null) {
      previous.socket.close();
    }
  }
}
