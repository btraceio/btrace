package org.openjdk.btrace.statsd;

import java.util.Formatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class QManager {
  final BlockingQueue<String> q = new ArrayBlockingQueue<>(120000);

  QManager() {}

  BlockingQueue<String> getQ() {
    return q;
  }

  private void appendSampleRate(double sampleRate, StringBuilder sb, Formatter fmt) {
    if (sampleRate > 0) {
      sb.append("|@");
      fmt.format("%.3f", sampleRate);
    }
  }

  void submit(String name, String value, String type, String tags) {
    StringBuilder sb = new StringBuilder(name);

    sb.append(':').append(value).append('|').append(type);
    appendTags(tags, sb);
    q.offer(sb.toString());
  }

  /**
   * Sends an event to a DogStatsD compatible collector
   *
   * @param title event name
   * @param text event text
   * @param timestamp Assign a timestamp to the event. 0 means the current date
   * @param host Assign a hostname to the event. May be null
   * @param group Assign an aggregation key to the event, to group it with some others. May be null
   * @param sourceType Assign a source type to the event. May be null
   * @param priority {@linkplain Statsd.Priority} - may be null for NORMAL
   * @param alertType {@linkplain Statsd.AlertType} - may be null for INFO
   * @param tags Assigned comma delimited tags. A tag value is delimited by colon.
   */
  public void event(
      String title,
      String text,
      long timestamp,
      String host,
      String group,
      String sourceType,
      Statsd.Priority priority,
      Statsd.AlertType alertType,
      String tags) {
    StringBuilder sb = new StringBuilder("_e{");
    sb.append(title.length()).append(',').append(text.length()).append('}');

    sb.append(':').append(title).append('|').append(text);

    if (timestamp >= 0) {
      sb.append("|d:").append(timestamp == 0 ? System.currentTimeMillis() : timestamp);
    }
    if (host != null) {
      sb.append("|h:").append(host);
    }
    if (group != null) {
      sb.append("|k:").append(group);
    }
    if (sourceType != null) {
      sb.append("|s:").append(sourceType);
    }
    if (priority != null) {
      sb.append("|p:").append(priority);
    }
    if (alertType != null) {
      sb.append("|t:").append(alertType);
    }
    appendTags(tags, sb);

    q.offer(sb.toString());
  }

  void delta(String name, long value, double sampleRate, String tags) {
    StringBuilder sb = new StringBuilder(name);
    Formatter fmt = new Formatter(sb);

    sb.append(':');
    if (value > 0) {
      sb.append('+');
    } else if (value < 0) {
      sb.append('-');
    }
    sb.append(value).append('|').append('g');
    appendSampleRate(sampleRate, sb, fmt);
    appendTags(tags, sb);
    q.offer(sb.toString());
  }

  private void appendTags(String tags, StringBuilder sb) {
    if (tags != null && !tags.isEmpty()) {
      sb.append("|#").append(tags);
    }
  }

  void submit(String name, long value, double sampleRate, String type, String tags) {
    StringBuilder sb = new StringBuilder(name);
    Formatter fmt = new Formatter(sb);

    sb.append(':').append(value).append('|').append(type);
    appendSampleRate(sampleRate, sb, fmt);
    appendTags(tags, sb);
    q.offer(sb.toString());
  }

  /**
   * Decrease the given counter by 1
   *
   * @param name the counter name
   */
  void decrement(String name) {
    delta(name, -1, 0.0d, null);
  }
}
