package org.openjdk.btrace.statsd;

import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/** API for StatsD metrics client. */
@ServiceDescriptor(permissions = { Permission.NETWORK })
public interface Statsd {
  void increment(String name);
  void increment(String name, String tags);
}
