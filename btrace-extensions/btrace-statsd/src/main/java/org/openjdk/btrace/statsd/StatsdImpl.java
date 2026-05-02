package org.openjdk.btrace.statsd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.Permission;

public final class StatsdImpl extends Extension implements Statsd {
  @Override
  public void increment(String name) {
    increment(name, null);
  }

  @Override
  public void increment(String name, String tags) {
    try {
      StringBuilder sb = new StringBuilder();
      sb.append(name).append(":1|c");
      if (tags != null && !tags.isEmpty()) {
        sb.append("|#").append(tags);
      }
      byte[] data = sb.toString().getBytes(StandardCharsets.US_ASCII);
      DatagramPacket pkt = new DatagramPacket(data, data.length);
      pkt.setAddress(InetAddress.getByName(SharedSettings.GLOBAL.getStatsdHost()));
      pkt.setPort(SharedSettings.GLOBAL.getStatsdPort());
      try (DatagramSocket socket = new DatagramSocket()) {
        socket.send(pkt);
      }
    } catch (Throwable ignore) {
      // Best-effort, ignore errors in script path
    }
  }
}
